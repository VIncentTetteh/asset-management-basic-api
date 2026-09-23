package com.assetiq.services.impl;

import com.assetiq.enums.UserStatus;
import com.assetiq.models.Organisation;
import com.assetiq.models.Role;
import com.assetiq.models.RolePermission;
import com.assetiq.models.User;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.DepartmentRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.RoleRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.security.PermissionCacheService;
import com.assetiq.security.RbacAuditService;
import com.assetiq.services.EmailService;
import com.assetiq.services.SessionRevocationService;
import com.assetiq.services.UsageLimitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * An organisation must never be left with nobody who can administer it.
 *
 * <p>Self-deletion already refused this. The administrative paths did not, so
 * one administrator could lock the tenant by acting on another: deactivate them,
 * move them to a viewer role, or take their role away, and support has to
 * intervene to get the company back.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("The last administrator cannot be removed")
class UserServiceImplLastAdminTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UsageLimitService usageLimitService;
    @Mock EmailService emailService;
    @Mock PermissionCacheService permissionCacheService;
    @Mock SessionRevocationService sessionRevocationService;
    @Mock RbacAuditService rbacAuditService;
    @Mock com.assetiq.services.UserErasureService userErasureService;

    private UserServiceImpl service;
    private Organisation org;
    private User onlyAdmin;
    private Role adminRole;
    private Role viewerRole;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, roleRepository, departmentRepository, organisationRepository,
                passwordEncoder, usageLimitService, emailService, permissionCacheService, sessionRevocationService,
                rbacAuditService, userErasureService);

        org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));

        adminRole = role("ADMIN", true);
        viewerRole = role("VIEWER", false, "VIEW_ASSETS");

        onlyAdmin = new User();
        onlyAdmin.setId(UUID.randomUUID());
        onlyAdmin.setFirstName("Akua");
        onlyAdmin.setLastName("Boateng");
        onlyAdmin.setEmail("akua@example.com");
        onlyAdmin.setOrganisation(org);
        onlyAdmin.setStatus(UserStatus.ACTIVE);
        onlyAdmin.setRole(adminRole);

        when(userRepository.findByIdAndOrganisation(onlyAdmin.getId(), org)).thenReturn(Optional.of(onlyAdmin));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        // The only administrator in the organisation is the one being acted on.
        when(userRepository.findByOrganisationAndRole_NameContainingIgnoreCaseAndDeletedAtIsNull(org, "ADMIN"))
                .thenReturn(List.of(onlyAdmin));

        actAs("other-admin@example.com", "ROLE_ADMIN");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("deactivating them is refused")
    void deactivationIsRefused() {
        assertThatThrownBy(() -> service.deactivateUser(onlyAdmin.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only active administrator");
        assertThat(onlyAdmin.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("moving them to a role that is not an administrator role is refused")
    void demotionIsRefused() {
        assertThatThrownBy(() -> service.assignRole(onlyAdmin.getId(), viewerRole.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only active administrator");
        assertThat(onlyAdmin.getRole()).isSameAs(adminRole);
    }

    @Test
    @DisplayName("taking their role away entirely is refused")
    void clearingTheRoleIsRefused() {
        assertThatThrownBy(() -> service.clearRole(onlyAdmin.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only active administrator");
        assertThat(onlyAdmin.getRole()).isSameAs(adminRole);
    }

    @Test
    @DisplayName("moving them to another administrator role is allowed")
    void promotionToAnotherAdminRoleIsAllowed() {
        Role otherAdmin = role("ORG_ADMIN", true);

        assertThat(service.assignRole(onlyAdmin.getId(), otherAdmin.getId()).getRoleId())
                .isEqualTo(otherAdmin.getId());
    }

    @Test
    @DisplayName("with a second active administrator, demoting the first is allowed again")
    void withAColleagueTheGuardStandsDown() {
        User secondAdmin = new User();
        secondAdmin.setId(UUID.randomUUID());
        secondAdmin.setOrganisation(org);
        secondAdmin.setStatus(UserStatus.ACTIVE);
        secondAdmin.setRole(adminRole);
        when(userRepository.findByOrganisationAndRole_NameContainingIgnoreCaseAndDeletedAtIsNull(org, "ADMIN"))
                .thenReturn(List.of(onlyAdmin, secondAdmin));

        assertThat(service.assignRole(onlyAdmin.getId(), viewerRole.getId()).getRoleId())
                .isEqualTo(viewerRole.getId());
    }

    @Test
    @DisplayName("a deactivated second administrator does not count as cover")
    void anInactiveAdministratorIsNotCover() {
        User dormant = new User();
        dormant.setId(UUID.randomUUID());
        dormant.setOrganisation(org);
        dormant.setStatus(UserStatus.INACTIVE);
        dormant.setRole(adminRole);
        when(userRepository.findByOrganisationAndRole_NameContainingIgnoreCaseAndDeletedAtIsNull(org, "ADMIN"))
                .thenReturn(List.of(onlyAdmin, dormant));

        assertThatThrownBy(() -> service.deactivateUser(onlyAdmin.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    private Role role(String name, boolean grantAll, String... permissions) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName(name);
        role.setGrantAllPermissions(grantAll);
        for (String permission : permissions) {
            RolePermission rp = new RolePermission();
            rp.setRole(role);
            rp.setPermission(permission);
            role.getRolePermissions().add(rp);
        }
        when(roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(role.getId(), org))
                .thenReturn(Optional.of(role));
        return role;
    }

    private static void actAs(String email, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, "n/a",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
}
