package com.assetiq.services.impl;

import com.assetiq.dto.UserDto;
import com.assetiq.enums.UserStatus;
import com.assetiq.models.Department;
import com.assetiq.models.Organisation;
import com.assetiq.models.Role;
import com.assetiq.models.User;
import com.assetiq.repositories.*;
import com.assetiq.services.EmailService;
import com.assetiq.services.TenantAwareService;
import com.assetiq.services.UsageLimitService;
import com.assetiq.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.email.base-url:http://localhost:3000}")
    private String baseUrl;

    public UserServiceImpl(UserRepository userRepository,
            RoleRepository roleRepository,
            DepartmentRepository departmentRepository,
            OrganisationRepository organisationRepository,
            PasswordEncoder passwordEncoder,
            UsageLimitService usageLimitService,
            EmailService emailService) {
        super(organisationRepository);
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.usageLimitService = usageLimitService;
        this.emailService = emailService;
    }

    @Override
    public UserDto createUser(UserDto dto) {
        Organisation org = requireTenantOrg();
        usageLimitService.assertCanCreateEmployee(org);

        // Prevent duplicate email within this org
        userRepository.findByEmailAndOrganisationId(dto.getEmail(), org.getId()).ifPresent(u -> {
            throw new IllegalStateException("A user with this email already exists in the organisation");
        });

        User user = new User();
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setEmployeeId(dto.getEmployeeId());
        user.setJobTitle(dto.getJobTitle());
        user.setOrganisation(org);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(
                passwordEncoder.encode(dto.getPassword() != null ? dto.getPassword() : UUID.randomUUID().toString()));

        if (dto.getRoleId() != null) {
            Role role = roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getRoleId(), org)
                    .orElseThrow(() -> new IllegalArgumentException("Role not found in your organisation"));
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
    public UserDto getUserById(UUID id) {
        Organisation org = requireTenantOrg();
        User user = userRepository.findByIdAndOrganisation(id, org)
                .orElseThrow(() -> new IllegalArgumentException("User not found in your organisation"));
        return toDto(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UserDto> listUsers() {
        Organisation org = requireTenantOrg();
        return userRepository.findByOrganisationAndDeletedAtIsNull(org).stream()
                .map(this::toDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
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
    public UserDto updateUser(UUID id, UserDto dto) {
        Organisation org = requireTenantOrg();
        User user = userRepository.findByIdAndOrganisation(id, org)
                .orElseThrow(() -> new IllegalArgumentException("User not found in your organisation"));

        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setPhone(dto.getPhone());
        user.setEmployeeId(dto.getEmployeeId());
        user.setJobTitle(dto.getJobTitle());

        if (dto.getDepartmentId() != null) {
            Department dept = departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getDepartmentId(), org)
                    .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));
            user.setDepartment(dept);
        }

        return toDto(userRepository.save(user));
    }

    @Override
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
        if (dto.getEmployeeId() != null) {
            user.setEmployeeId(dto.getEmployeeId());
        }
        if (dto.getJobTitle() != null) {
            user.setJobTitle(dto.getJobTitle());
        }
        if (dto.getDepartmentId() != null) {
            Department dept = departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getDepartmentId(), org)
                    .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));
            user.setDepartment(dept);
        }
        if (dto.getStatus() != null) {
            user.setStatus(dto.getStatus());
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
    public UserDto deactivateUser(UUID id) {
        Organisation org = requireTenantOrg();
        User user = userRepository.findByIdAndOrganisation(id, org)
                .orElseThrow(() -> new IllegalArgumentException("User not found in your organisation"));
        user.setStatus(UserStatus.INACTIVE);
        return toDto(userRepository.save(user));
    }

    @Override
    public UserDto assignRole(UUID userId, UUID roleId) {
        Organisation org = requireTenantOrg();
        User user = userRepository.findByIdAndOrganisation(userId, org)
                .orElseThrow(() -> new IllegalArgumentException("User not found in your organisation"));
        Role role = roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(roleId, org)
                .orElseThrow(() -> new IllegalArgumentException("Role not found in your organisation"));
        user.setRole(role);
        return toDto(userRepository.save(user));
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
