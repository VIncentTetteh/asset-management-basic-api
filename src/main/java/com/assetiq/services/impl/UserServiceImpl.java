package com.assetiq.services.impl;

import com.assetiq.models.RolePermission;
import com.assetiq.security.PermissionGrantGuard;
import com.assetiq.security.RolePermissionDefaults;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.assetiq.dto.UserDto;
import com.assetiq.config.CachingConfig;
import com.assetiq.enums.UserStatus;
import com.assetiq.models.Department;
import com.assetiq.models.Organisation;
import com.assetiq.models.Role;
import com.assetiq.models.User;
import com.assetiq.repositories.*;
import com.assetiq.security.PermissionCacheService;
import com.assetiq.security.RbacAuditService;
import com.assetiq.services.EmailService;
import com.assetiq.services.TenantAwareService;
import com.assetiq.services.UsageLimitService;
import com.assetiq.services.UserService;
import com.assetiq.services.SessionRevocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserServiceImpl extends TenantAwareService implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsageLimitService usageLimitService;
    private final EmailService emailService;
    private final PermissionCacheService permissionCacheService;
    private final SessionRevocationService sessionRevocationService;
    private final RbacAuditService rbacAuditService;

    @Value("${app.email.base-url:http://localhost:3000}")
    private String baseUrl;

    public UserServiceImpl(UserRepository userRepository,
            RoleRepository roleRepository,
            DepartmentRepository departmentRepository,
            OrganisationRepository organisationRepository,
            PasswordEncoder passwordEncoder,
            UsageLimitService usageLimitService,
            EmailService emailService,
            PermissionCacheService permissionCacheService,
            SessionRevocationService sessionRevocationService,
            RbacAuditService rbacAuditService) {
        super(organisationRepository);
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.usageLimitService = usageLimitService;
        this.emailService = emailService;
        this.permissionCacheService = permissionCacheService;
        this.sessionRevocationService = sessionRevocationService;
        this.rbacAuditService = rbacAuditService;
    }

    @Override
    @CacheEvict(value = CachingConfig.CacheNames.USERS, allEntries = true)
    public UserDto createUser(UserDto dto) {
        Organisation org = requireTenantOrg();
        usageLimitService.assertCanCreateEmployee(org);

        // Prevent duplicate email within this org
        userRepository.findByEmailAndOrganisationId(dto.getEmail(), org.getId()).ifPresent(u -> {
            throw new IllegalStateException("A user with this email already exists in the organisation");
        });

        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new IllegalArgumentException("A temporary password is required for a new user");
        }

        User user = new User();
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        // Same generator as tenant registration and /auth/register: users created
        // here used to get no employee id at all.
        user.setEmployeeId(com.assetiq.models.EmployeeIds.orGenerate(dto.getEmployeeId()));
        user.setJobTitle(dto.getJobTitle());
        user.setOrganisation(org);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(
                passwordEncoder.encode(dto.getPassword() != null ? dto.getPassword() : UUID.randomUUID().toString()));

        if (dto.getRoleId() != null) {
            Role role = roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getRoleId(), org)
                    .orElseThrow(() -> new IllegalArgumentException("Role not found in your organisation"));
            assertCanGrant(role);
            user.setRole(role);
        }

        if (dto.getDepartmentId() != null) {
            Department dept = departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getDepartmentId(), org)
                    .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));
            user.setDepartment(dept);
        }

        User saved = userRepository.save(user);

        // Send welcome / invite email to the newly created user
        try {
            String roleName = (saved.getRole() != null) ? saved.getRole().getName() : null;
            Map<String, Object> model = new java.util.HashMap<>();
            model.put("firstName", saved.getFirstName() != null ? saved.getFirstName() : "");
            model.put("email", saved.getEmail());
            model.put("temporaryPassword", dto.getPassword() != null ? dto.getPassword() : "(set by admin)");
            model.put("organisationName", org.getName() != null ? org.getName() : "");
            model.put("loginUrl", baseUrl + "/login");
            model.put("role", roleName != null ? roleName : "");
            emailService.sendTemplate(
                saved.getEmail(),
                "You've been invited to " + org.getName(),
                "email/user-invite",
                model
            );
        } catch (Exception e) {
            log.warn("[EMAIL] Failed to send invite email to {}: {}", saved.getEmail(), e.getMessage());
        }

        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CachingConfig.CacheNames.USERS, key = "T(com.assetiq.multitenancy.TenantContext).getOrganisationId().toString() + ':one:' + #id.toString()")
    public UserDto getUserById(UUID id) {
        Organisation org = requireTenantOrg();
        User user = userRepository.findByIdAndOrganisation(id, org)
                .orElseThrow(() -> new IllegalArgumentException("User not found in your organisation"));
        return toDto(user);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CachingConfig.CacheNames.USERS, key = "T(com.assetiq.multitenancy.TenantContext).getOrganisationId().toString() + ':list'")
    public Set<UserDto> listUsers() {
        Organisation org = requireTenantOrg();
        return userRepository.findByOrganisationAndDeletedAtIsNull(org).stream()
                .map(this::toDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CachingConfig.CacheNames.USERS, key = "T(com.assetiq.multitenancy.TenantContext).getOrganisationId().toString() + ':department:' + #departmentId.toString()")
    public Set<UserDto> listUsersByDepartment(UUID departmentId) {
        Organisation org = requireTenantOrg();
        departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(departmentId, org)
                .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));
        return userRepository.findByDepartmentId(departmentId).stream()
                .filter(u -> u.getDeletedAt() == null)
                .map(this::toDto)
                .collect(Collectors.toSet());
    }

    @Override
    @CacheEvict(value = CachingConfig.CacheNames.USERS, allEntries = true)
    public UserDto updateUser(UUID id, UserDto dto) {
        Organisation org = requireTenantOrg();
        User user = userRepository.findByIdAndOrganisation(id, org)
                .orElseThrow(() -> new IllegalArgumentException("User not found in your organisation"));

        // PUT replaces the profile: an omitted optional field (department included)
        // is cleared. Email, role and status have their own flows and are ignored.
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setPhone(dto.getPhone());
        // The id is required: a PUT that omits it keeps the current one.
        if (dto.getEmployeeId() != null && !dto.getEmployeeId().isBlank()) {
            user.setEmployeeId(dto.getEmployeeId().trim());
        }
        user.setJobTitle(dto.getJobTitle());
        user.setDepartment(dto.getDepartmentId() == null ? null
                : departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getDepartmentId(), org)
                        .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation")));

        return toDto(userRepository.save(user));
    }

    @Override
    @CacheEvict(value = CachingConfig.CacheNames.USERS, allEntries = true)
    public UserDto patchUser(UUID id, UserDto dto) {
        Organisation org = requireTenantOrg();
        User user = userRepository.findByIdAndOrganisation(id, org)
                .orElseThrow(() -> new IllegalArgumentException("User not found in your organisation"));

        if (dto.getFirstName() != null) {
            user.setFirstName(dto.getFirstName());
        }
        if (dto.getLastName() != null) {
            user.setLastName(dto.getLastName());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }
        if (dto.getEmployeeId() != null && !dto.getEmployeeId().isBlank()) {
            user.setEmployeeId(dto.getEmployeeId().trim());
        }
        if (dto.getJobTitle() != null) {
            user.setJobTitle(dto.getJobTitle());
        }
        if (dto.getDepartmentId() != null) {
            Department dept = departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getDepartmentId(), org)
                    .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));
            user.setDepartment(dept);
        }
        if (dto.getStatus() != null && dto.getStatus() != user.getStatus()) {
            // Status changes lock people out or let them back in; they go through
            // /deactivate and /activate, which require a fresh MFA step-up. PATCH
            // used to change status with no step-up at all.
            throw new IllegalArgumentException("Change a user's status with /deactivate or /activate");
        }

        return toDto(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getMe(String email) {
        Organisation org = requireTenantOrg();
        User user = userRepository.findByEmailAndOrganisationId(email, org.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return toDto(user);
    }

    @Override
    @CacheEvict(value = CachingConfig.CacheNames.USERS, allEntries = true)
    public UserDto patchMe(String email, UserDto dto) {
        Organisation org = requireTenantOrg();
        User user = userRepository.findByEmailAndOrganisationId(email, org.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Only allow updating safe personal fields — no role/status/dept changes
        if (dto.getFirstName() != null && !dto.getFirstName().isBlank()) {
            user.setFirstName(dto.getFirstName());
        }
        if (dto.getLastName() != null && !dto.getLastName().isBlank()) {
            user.setLastName(dto.getLastName());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone().isBlank() ? null : dto.getPhone());
        }
        if (dto.getJobTitle() != null) {
            user.setJobTitle(dto.getJobTitle().isBlank() ? null : dto.getJobTitle());
        }

        return toDto(userRepository.save(user));
    }

    @Override
    public void changeOwnPassword(String email, com.assetiq.dto.ChangePasswordRequest request) {
        Organisation org = requireTenantOrg();
        User user = userRepository.findByEmailAndOrganisationId(email, org.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new com.assetiq.exceptions.FieldValidationException("currentPassword",
                    "The current password is not correct");
        }
        if (request.getNewPassword().equals(request.getCurrentPassword())) {
            throw new com.assetiq.exceptions.FieldValidationException("newPassword",
                    "Choose a password different from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        // A changed password must end every existing session, as a reset does.
        sessionRevocationService.revokeAll(user);
        rbacAuditService.recordPasswordChanged(user.getId());
    }

    @Override
    @CacheEvict(value = CachingConfig.CacheNames.USERS, allEntries = true)
    public UserDto deactivateUser(UUID id) {
        Organisation org = requireTenantOrg();
        User user = userRepository.findByIdAndOrganisation(id, org)
                .orElseThrow(() -> new IllegalArgumentException("User not found in your organisation"));
        if (isCurrentUser(user)) {
            throw new IllegalStateException("You cannot deactivate your own account");
        }
        user.setStatus(UserStatus.INACTIVE);
        sessionRevocationService.revokeAll(user);
        return toDto(user);
    }

    @Override
    @CacheEvict(value = CachingConfig.CacheNames.USERS, allEntries = true)
    public UserDto activateUser(UUID id) {
        Organisation org = requireTenantOrg();
        User user = userRepository.findByIdAndOrganisation(id, org)
                .orElseThrow(() -> new IllegalArgumentException("User not found in your organisation"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            // Reactivation takes a seat back, so it answers to the plan limit the
            // same way creating a user does; it used to skip the check entirely.
            usageLimitService.assertCanActivateUser(org);
            user.setStatus(UserStatus.ACTIVE);
            userRepository.save(user);
        }
        return toDto(user);
    }

    private boolean isCurrentUser(User user) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && user.getEmail() != null && user.getEmail().equalsIgnoreCase(auth.getName());
    }

    /**
     * Privilege-escalation guard: a user manager may only hand out a role whose
     * permissions they hold themselves. Without it anyone with MANAGE_USERS or
     * EDIT_USER could assign the grant-all ADMIN role (to themselves via a
     * helper account, or to anyone). Organisation admins are unrestricted.
     */
    void assertCanGrant(Role role) {
        Set<String> required = role.isGrantAllPermissions()
                ? RolePermissionDefaults.allPermissionNames()
                : role.getRolePermissions().stream().map(RolePermission::getPermission).collect(Collectors.toSet());
        PermissionGrantGuard.assertCallerHolds(required, "assign the role '" + role.getName() + "'");
    }

    @Override
    @CacheEvict(value = CachingConfig.CacheNames.USERS, allEntries = true)
    public UserDto assignRole(UUID userId, UUID roleId) {
        Organisation org = requireTenantOrg();
        User user = userRepository.findByIdAndOrganisation(userId, org)
                .orElseThrow(() -> new IllegalArgumentException("User not found in your organisation"));
        Role role = roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(roleId, org)
                .orElseThrow(() -> new IllegalArgumentException("Role not found in your organisation"));
        if (isCurrentUser(user) && (user.getRole() == null || !user.getRole().getId().equals(role.getId()))) {
            throw new IllegalStateException("You cannot change your own role; ask another administrator");
        }
        assertCanGrant(role);
        String oldRoleName = user.getRole() == null ? null : user.getRole().getName();
        if (user.getRole() == null || !user.getRole().getId().equals(role.getId())) {
            user.setRole(role);
            sessionRevocationService.revokeAll(user);
            rbacAuditService.recordUserRoleAssigned(userId, oldRoleName, role.getName());
        }
        UserDto saved = toDto(user);
        permissionCacheService.evictForUser(user.getEmail(), org.getId().toString());
        return saved;
    }

    @Override
    @CacheEvict(value = CachingConfig.CacheNames.USERS, allEntries = true)
    public UserDto clearRole(UUID userId) {
        Organisation org = requireTenantOrg();
        User user = userRepository.findByIdAndOrganisation(userId, org)
                .orElseThrow(() -> new IllegalArgumentException("User not found in your organisation"));
        if (isCurrentUser(user)) {
            throw new IllegalStateException("You cannot change your own role; ask another administrator");
        }
        if (user.getRole() != null) {
            String oldRoleName = user.getRole().getName();
            user.setRole(null);
            // Removing the role removes every permission, so live sessions must go.
            sessionRevocationService.revokeAll(user);
            rbacAuditService.recordUserRoleAssigned(userId, oldRoleName, null);
        }
        UserDto saved = toDto(user);
        permissionCacheService.evictForUser(user.getEmail(), org.getId().toString());
        return saved;
    }

    private UserDto toDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setEmployeeId(user.getEmployeeId());
        dto.setJobTitle(user.getJobTitle());
        dto.setStatus(user.getStatus());
        dto.setOrganisationId(user.getOrganisation() != null ? user.getOrganisation().getId() : null);
        dto.setDepartmentId(user.getDepartment() != null ? user.getDepartment().getId() : null);
        dto.setRoleId(user.getRole() != null ? user.getRole().getId() : null);
        dto.setMfaEnabled(Boolean.TRUE.equals(user.getMfaEnabled()));
        // never return password hash
        return dto;
    }
}
