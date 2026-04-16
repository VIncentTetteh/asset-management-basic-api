package com.assetiq.services.impl;

import com.assetiq.dto.RoleDto;
import com.assetiq.enums.Permission;
import com.assetiq.models.Organisation;
import com.assetiq.models.Role;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.RoleRepository;
import com.assetiq.security.PermissionCacheService;
import com.assetiq.security.RbacAuditService;
import com.assetiq.security.RolePermissionDefaults;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RoleServiceImpl}.
 *
 * Phase 2 / B-1: permissions are now {@code List<String>} (no JSON encoding);
 * tests set and assert typed lists directly.
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private OrganisationRepository organisationRepository;

    @Mock
    private PermissionCacheService permissionCacheService;

    @Mock
    private RbacAuditService rbacAuditService;

    @InjectMocks
    private RoleServiceImpl roleService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── createRole ────────────────────────────────────────────────────────────

    @Test
    void createRole_withGrantAllFlag_returnsEveryPermission() {
        Organisation organisation = tenantOrganisation();
        RoleDto dto = new RoleDto();
        dto.setName("ORG_ADMIN");
        dto.setGrantAllPermissions(true);
        // Requested permissions should be ignored when grantAll=true
        dto.setPermissions(List.of("VIEW_ASSETS"));

        when(organisationRepository.findByIdAndDeletedAtIsNull(organisation.getId()))
                .thenReturn(Optional.of(organisation));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleDto created = roleService.createRole(dto, organisation.getId());

        Set<String> expected = Arrays.stream(Permission.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
        assertEquals(expected, Set.copyOf(created.getPermissions()),
                "grantAll=true should produce every permission regardless of the requested list");
    }

    @Test
    void createRole_withoutGrantAllFlag_preservesExplicitPermissions() {
        Organisation organisation = tenantOrganisation();
        RoleDto dto = new RoleDto();
        dto.setName("ASSET_MANAGER");
        dto.setPermissions(List.of("VIEW_ASSETS", "EDIT_ASSET"));

        when(organisationRepository.findByIdAndDeletedAtIsNull(organisation.getId()))
                .thenReturn(Optional.of(organisation));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleDto created = roleService.createRole(dto, organisation.getId());

        assertEquals(List.of("EDIT_ASSET", "VIEW_ASSETS"), created.getPermissions(),
                "Non-grant-all role should store exactly the requested permissions (sorted)");
    }

    // ── patchRole ─────────────────────────────────────────────────────────────

    @Test
    void patchRole_withExplicitPermissions_evictsCache() {
        Organisation organisation = tenantOrganisation();
        Role existingRole = roleWithOrg(organisation);

        RoleDto patch = new RoleDto();
        patch.setPermissions(List.of("VIEW_ASSETS", "MANAGE_ROLES"));

        when(organisationRepository.findByIdAndDeletedAtIsNull(organisation.getId()))
                .thenReturn(Optional.of(organisation));
        when(roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(existingRole.getId(), organisation))
                .thenReturn(Optional.of(existingRole));
        when(roleRepository.save(existingRole)).thenReturn(existingRole);

        RoleDto updated = roleService.patchRole(existingRole.getId(), patch);

        // Permissions in the patch were applied and cache was evicted.
        assertTrue(updated.getPermissions().containsAll(List.of("VIEW_ASSETS", "MANAGE_ROLES")));
        verify(permissionCacheService).evictForRole(existingRole.getId());
    }

    @Test
    void patchRole_withoutPermissions_doesNotEvictCache() {
        Organisation organisation = tenantOrganisation();
        Role existingRole = roleWithOrg(organisation);

        RoleDto patch = new RoleDto();
        patch.setName("Updated Name");
        // permissions = null → no permission change

        when(organisationRepository.findByIdAndDeletedAtIsNull(organisation.getId()))
                .thenReturn(Optional.of(organisation));
        when(roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(existingRole.getId(), organisation))
                .thenReturn(Optional.of(existingRole));
        when(roleRepository.save(existingRole)).thenReturn(existingRole);

        roleService.patchRole(existingRole.getId(), patch);

        // Permissions not in the patch → cache must not be evicted (B-3 targeted eviction).
        verify(permissionCacheService, never()).evictForRole(existingRole.getId());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Organisation tenantOrganisation() {
        Organisation organisation = new Organisation();
        organisation.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(organisation.getId());
        return organisation;
    }

    private Role roleWithOrg(Organisation organisation) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName("VIEWER");
        role.setOrganisation(organisation);
        return role;
    }
}
