package com.assetiq.services.impl;

import com.assetiq.dto.UserDto;
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
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("User administration cannot escalate privileges or lock the actor out")
class UserServiceImplSecurityTest {

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

    private UserServiceImpl service;
    private Organisation org;
    private User target;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, roleRepository, departmentRepository, organisationRepository,
                passwordEncoder, usageLimitService, emailService, permissionCacheService, sessionRevocationService,
                rbacAuditService);
        org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
        target = new User();
        target.setId(UUID.randomUUID());
        target.setEmail("someone@example.com");
        target.setOrganisation(org);
        target.setStatus(UserStatus.ACTIVE);
        when(userRepository.findByIdAndOrganisation(target.getId(), org)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void actAs(String email, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, "n/a",
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private Role role(boolean grantAll, String... perms) {
        Role r = new Role();
        r.setId(UUID.randomUUID());
        r.setName(grantAll ? "ADMIN" : "CUSTOM");
        r.setGrantAllPermissions(grantAll);
        for (String p : perms) {
            RolePermission rp = new RolePermission();
            rp.setRole(r);
            rp.setPermission(p);
            r.getRolePermissions().add(rp);
        }
        when(roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(r.getId(), org)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void userManagerCannotHandOutTheAdminRole() {
        Role admin = role(true);
        actAs("hr@example.com", "MANAGE_USERS", "VIEW_USERS");
        assertThatThrownBy(() -> service.assignRole(target.getId(), admin.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void userManagerCanAssignARoleWithinTheirOwnPermissions() {
        Role viewer = role(false, "VIEW_USERS");
        actAs("hr@example.com", "MANAGE_USERS", "VIEW_USERS");
        assertThat(service.assignRole(target.getId(), viewer.getId()).getRoleId()).isEqualTo(viewer.getId());
    }

    @Test
    void orgAdminIsUnrestrictedButCannotChangeOwnRole() {
        Role admin = role(true);
        actAs("boss@example.com", "ROLE_ADMIN");
        assertThat(service.assignRole(target.getId(), admin.getId()).getRoleId()).isEqualTo(admin.getId());

        actAs("someone@example.com", "ROLE_ADMIN");
        Role other = role(false, "VIEW_USERS");
        assertThatThrownBy(() -> service.assignRole(target.getId(), other.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotDeactivateYourselfAndPatchCannotChangeStatus() {
        actAs("someone@example.com", "ROLE_ADMIN");
        assertThatThrownBy(() -> service.deactivateUser(target.getId())).isInstanceOf(IllegalStateException.class);

        actAs("boss@example.com", "ROLE_ADMIN");
        UserDto patch = new UserDto();
        patch.setStatus(UserStatus.INACTIVE);
        assertThatThrownBy(() -> service.patchUser(target.getId(), patch)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deactivatedUserCanBeReactivated() {
        target.setStatus(UserStatus.INACTIVE);
        actAs("boss@example.com", "ROLE_ADMIN");
        assertThat(service.activateUser(target.getId()).getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void putClearsTheDepartmentAndCreateNeedsAPassword() {
        actAs("boss@example.com", "ROLE_ADMIN");
        target.setDepartment(new com.assetiq.models.Department());
        UserDto put = new UserDto();
        put.setFirstName("A");
        put.setLastName("B");
        put.setEmail(target.getEmail());
        assertThat(service.updateUser(target.getId(), put).getDepartmentId()).isNull();

        UserDto create = new UserDto();
        create.setFirstName("New");
        create.setLastName("User");
        create.setEmail("new@example.com");
        assertThatThrownBy(() -> service.createUser(create)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usersCreatedHereGetAnEmployeeIdAndPutKeepsIt() {
        actAs("boss@example.com", "ROLE_ADMIN");
        when(passwordEncoder.encode(ArgumentMatchers.anyString())).thenReturn("hash");
        UserDto create = new UserDto();
        create.setFirstName("New");
        create.setLastName("User");
        create.setEmail("new@example.com");
        create.setPassword("correct horse battery");

        UserDto created = service.createUser(create);
        assertThat(created.getEmployeeId()).matches("EMP-[0-9A-F]{10}");

        create.setEmail("other@example.com");
        create.setEmployeeId(" HR-42 ");
        assertThat(service.createUser(create).getEmployeeId()).isEqualTo("HR-42");

        target.setEmployeeId("EMP-KEEP");
        UserDto put = new UserDto();
        put.setFirstName("A");
        put.setLastName("B");
        assertThat(service.updateUser(target.getId(), put).getEmployeeId()).isEqualTo("EMP-KEEP");
    }
}
