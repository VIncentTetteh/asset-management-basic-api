package com.assetiq.services.impl;

import com.assetiq.dto.RoleDto;
import com.assetiq.models.Organisation;
import com.assetiq.models.Role;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.RoleRepository;
import com.assetiq.security.PermissionCacheService;
import com.assetiq.security.RolePermissionDefaults;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private OrganisationRepository organisationRepository;

    @Mock
    private PermissionCacheService permissionCacheService;

    @InjectMocks
    private RoleServiceImpl roleService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createRole_assignsAllPermissionsToAdminRoles() {
        Organisation organisation = tenantOrganisation();
        RoleDto dto = new RoleDto();
        dto.setName("ORG_ADMIN");
        dto.setPermissions("[\"VIEW_ASSETS\"]");

        when(organisationRepository.findByIdAndDeletedAtIsNull(organisation.getId())).thenReturn(Optional.of(organisation));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoleDto created = roleService.createRole(dto, organisation.getId());

        assertEquals(RolePermissionDefaults.allPermissionsJson(), created.getPermissions());
    }

    @Test
    void createRole_preservesExplicitPermissionsForNonAdminRoles() {
        Organisation organisation = tenantOrganisation();
        RoleDto dto = new RoleDto();
        dto.setName("ASSET_MANAGER");
        dto.setPermissions("[\"VIEW_ASSETS\"]");

        when(organisationRepository.findByIdAndDeletedAtIsNull(organisation.getId())).thenReturn(Optional.of(organisation));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoleDto created = roleService.createRole(dto, organisation.getId());

        assertEquals("[\"VIEW_ASSETS\"]", created.getPermissions());
    }

    @Test
    void patchRole_restoresFullPermissionsForExistingAdminRole() {
        Organisation organisation = tenantOrganisation();
        Role existingRole = new Role();
        existingRole.setId(UUID.randomUUID());
        existingRole.setName("ADMIN");
        existingRole.setPermissions("[\"VIEW_ASSETS\"]");
        existingRole.setOrganisation(organisation);

        RoleDto patch = new RoleDto();
        patch.setDescription("Updated admin description");

        when(organisationRepository.findByIdAndDeletedAtIsNull(organisation.getId())).thenReturn(Optional.of(organisation));
        when(roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(existingRole.getId(), organisation))
                .thenReturn(Optional.of(existingRole));
        when(roleRepository.save(existingRole)).thenReturn(existingRole);

        RoleDto updated = roleService.patchRole(existingRole.getId(), patch);

        assertEquals(RolePermissionDefaults.allPermissionsJson(), updated.getPermissions());
        verify(permissionCacheService).evictForRole(existingRole.getId());
    }

    @Test
    void patchRole_doesNotEvictPermissionsWhenNonAdminPermissionsDoNotChange() {
        Organisation organisation = tenantOrganisation();
        Role existingRole = new Role();
        existingRole.setId(UUID.randomUUID());
        existingRole.setName("VIEWER");
        existingRole.setPermissions("[\"VIEW_ASSETS\"]");
        existingRole.setOrganisation(organisation);

        RoleDto patch = new RoleDto();
        patch.setDescription("Read-only role");

        when(organisationRepository.findByIdAndDeletedAtIsNull(organisation.getId())).thenReturn(Optional.of(organisation));
        when(roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(existingRole.getId(), organisation))
                .thenReturn(Optional.of(existingRole));
        when(roleRepository.save(existingRole)).thenReturn(existingRole);

        RoleDto updated = roleService.patchRole(existingRole.getId(), patch);

        assertEquals("[\"VIEW_ASSETS\"]", updated.getPermissions());
        verify(permissionCacheService, never()).evictForRole(existingRole.getId());
    }

    private Organisation tenantOrganisation() {
        Organisation organisation = new Organisation();
        organisation.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(organisation.getId());
        return organisation;
    }
}
