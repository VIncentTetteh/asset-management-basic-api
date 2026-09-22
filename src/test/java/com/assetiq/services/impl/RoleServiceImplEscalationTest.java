package com.assetiq.services.impl;

import com.assetiq.dto.RoleDto;
import com.assetiq.models.Organisation;
import com.assetiq.models.Role;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.RoleRepository;
import com.assetiq.security.PermissionCacheService;
import com.assetiq.security.RbacAuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Role administration cannot mint more power than the caller has")
class RoleServiceImplEscalationTest {

    @Mock RoleRepository roleRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock PermissionCacheService permissionCacheService;
    @Mock RbacAuditService rbacAuditService;

    private RoleServiceImpl service;
    private Organisation org;

    @BeforeEach
    void setUp() {
        service = new RoleServiceImpl(roleRepository, organisationRepository, permissionCacheService, rbacAuditService);
        org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
        when(roleRepository.findByNameAndOrganisationAndDeletedAtIsNull(any(), any())).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> {
            Role r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void actAs(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("x@example.com",
                "n/a", Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private RoleDto dto(String name, String... perms) {
        RoleDto dto = new RoleDto();
        dto.setName(name);
        dto.setPermissions(List.of(perms));
        return dto;
    }

    @Test
    void roleManagerCannotCreateAGrantAllRoleOrGrantMissingPermissions() {
        actAs("MANAGE_ROLES", "VIEW_ASSETS");
        RoleDto all = dto("SUPER");
        all.setGrantAllPermissions(true);
        assertThatThrownBy(() -> service.createRole(all, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.createRole(dto("FIN", "APPROVE_BUDGET"), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(service.createRole(dto("VIEWERS", "VIEW_ASSETS"), null).getPermissions()).containsExactly("VIEW_ASSETS");
    }

    @Test
    void adminPatternNamesDuplicatesAndUnknownPermissionsAreRefused() {
        actAs("ROLE_ADMIN");
        assertThatThrownBy(() -> service.createRole(dto("HR_ADMIN", "VIEW_USERS"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createRole(dto("TYPO", "VIEW_ASSSETS"), null))
                .isInstanceOf(IllegalArgumentException.class);
        Role existing = new Role();
        existing.setId(UUID.randomUUID());
        when(roleRepository.findByNameAndOrganisationAndDeletedAtIsNull("AUDITORS", org)).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.createRole(dto("AUDITORS", "VIEW_ASSETS"), null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void roleInUseCannotBeDeleted() {
        actAs("ROLE_ADMIN");
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName("FIELD");
        when(roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(role.getId(), org)).thenReturn(Optional.of(role));
        when(roleRepository.isAssignedToAnyUser(role.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.deleteRole(role.getId())).isInstanceOf(IllegalStateException.class);
    }
}
