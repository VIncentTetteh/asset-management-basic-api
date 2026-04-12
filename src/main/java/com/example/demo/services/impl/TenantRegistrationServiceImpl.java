package com.example.demo.services.impl;

import com.example.demo.dto.TenantRegisterRequest;
import com.example.demo.dto.TenantRegisterResponse;
import com.example.demo.enums.Permission;
import com.example.demo.enums.UserStatus;
import com.example.demo.models.Organisation;
import com.example.demo.models.OrganisationSubscription;
import com.example.demo.models.Role;
import com.example.demo.models.SubscriptionPlan;
import com.example.demo.models.User;
import com.example.demo.repositories.OrganisationSubscriptionRepository;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.repositories.RoleRepository;
import com.example.demo.repositories.SubscriptionPlanRepository;
import com.example.demo.repositories.UserRepository;
import com.example.demo.security.JwtUtil;
import com.example.demo.security.RolePermissionDefaults;
import com.example.demo.services.EmailService;
import com.example.demo.services.TenantRegistrationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TenantRegistrationServiceImpl implements TenantRegistrationService {

    private final OrganisationRepository organisationRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final OrganisationSubscriptionRepository organisationSubscriptionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;

    @Value("${app.email.base-url:http://localhost:3000}")
    private String emailBaseUrl;

    public TenantRegistrationServiceImpl(OrganisationRepository organisationRepository,
            RoleRepository roleRepository,
            UserRepository userRepository,
            SubscriptionPlanRepository subscriptionPlanRepository,
            OrganisationSubscriptionRepository organisationSubscriptionRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            EmailService emailService) {
        this.organisationRepository = organisationRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.organisationSubscriptionRepository = organisationSubscriptionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public TenantRegisterResponse registerTenant(TenantRegisterRequest request) {
        // Basic validations
        if (organisationRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(request.getOrganisationName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organisation name already exists");
        }

        // Ensure admin email not used in any existing org with same name check is
        // enough because org is new,
        // but we can still enforce global uniqueness if desired (here we just check
        // globally).
        userRepository.findByEmail(request.getAdminEmail()).ifPresent(u -> {
            // Allow same email across different orgs? Existing code allows same email per
            // org unique. We'll allow.
        });

        // Create organisation
        Organisation org = new Organisation();
        org.setName(request.getOrganisationName());
        org.setContactEmail(request.getOrganisationContactEmail());
        org.setCountry(request.getCountry());
        org.setAddress(request.getAddress());
        org.setTimezone(request.getTimezone());
        org.setIndustry(request.getIndustry());
        org.setRegistrationNumber(request.getRegistrationNumber());
        org.setTaxId(request.getTaxId());
        org.setContactPhone(request.getContactPhone());
        org.setCreatedBy(request.getAdminEmail()); // Manually set for ownership filtering
        Organisation savedOrg = organisationRepository.save(org);

        // Ensure default roles exist (ADMIN, USER)
        Role adminRole = roleRepository.findByNameAndOrganisationId("ADMIN", savedOrg.getId()).orElseGet(() -> {
            Role r = new Role();
            r.setName("ADMIN");
            r.setDescription("Organisation administrator with full permissions");
            r.setPermissions(RolePermissionDefaults.allPermissionsJson());
            r.setOrganisation(savedOrg);
            r.setCreatedBy(request.getAdminEmail()); // Manually set for ownership filtering
            return roleRepository.save(r);
        });
        if (!Objects.equals(adminRole.getPermissions(), RolePermissionDefaults.allPermissionsJson())) {
            adminRole.setPermissions(RolePermissionDefaults.allPermissionsJson());
            adminRole = roleRepository.save(adminRole);
        }

        roleRepository.findByNameAndOrganisationId("USER", savedOrg.getId()).orElseGet(() -> {
            Role r = new Role();
            r.setName("USER");
            r.setDescription("Standard user role with limited permissions");
            // Minimal view permissions
            Set<String> perms = new HashSet<>(Arrays.asList(
                    Permission.VIEW_ASSETS.name(),
                    Permission.VIEW_USERS.name(),
                    Permission.VIEW_DEPARTMENTS.name(),
                    Permission.VIEW_REPORTS.name()));
            r.setPermissions(asJsonArray(perms));
            r.setOrganisation(savedOrg);
            r.setCreatedBy(request.getAdminEmail()); // Manually set for ownership filtering
            return roleRepository.save(r);
        });

        // Create initial admin user
        User user = new User();
        user.setFirstName(request.getAdminFirstName());
        user.setLastName(request.getAdminLastName());
        user.setEmail(request.getAdminEmail());
        // User.employeeId is non-null in the data model; generate a deterministic-enough value
        // for onboarding so the tenant registration endpoint works out-of-the-box.
        user.setEmployeeId("EMP-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase(Locale.ROOT));
        user.setPhone(request.getAdminPhone());
        user.setJobTitle(request.getAdminJobTitle());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(adminRole);
        user.setStatus(UserStatus.ACTIVE);
        user.setOrganisation(savedOrg);
        user.setCreatedBy(request.getAdminEmail()); // Manually set for ownership filtering
        User savedUser = userRepository.save(user);

        // Provision default freemium subscription for every new organisation
        SubscriptionPlan freemiumPlan = subscriptionPlanRepository.findByCodeAndDeletedAtIsNull("FREEMIUM")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Default FREEMIUM plan is not configured"));
        OrganisationSubscription subscription = new OrganisationSubscription();
        subscription.setOrganisation(savedOrg);
        subscription.setPlan(freemiumPlan);
        subscription.setStatus(com.example.demo.enums.SubscriptionStatus.ACTIVE);
        subscription.setAutoRenew(false);
        subscription.setCurrentPeriodStart(java.time.Instant.now());
        subscription.setCurrentPeriodEnd(java.time.Instant.now().plus(java.time.Duration.ofDays(365)));
        organisationSubscriptionRepository.save(subscription);

        // Build JWT claims similar to login
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", savedUser.getEmail());
        claims.put("firstName", savedUser.getFirstName());
        claims.put("lastName", savedUser.getLastName());
        String adminRoleName = adminRole.getName();
        claims.put("role", adminRoleName.startsWith("ROLE_") ? adminRoleName : "ROLE_" + adminRoleName);
        claims.put("permissions", adminRole.getPermissions());
        claims.put("organisationId", savedOrg.getId().toString());

        long expiresMillis = 1000L * 60 * 60 * 24; // 24h
        String token = jwtUtil.generateToken(savedUser.getEmail(), claims, expiresMillis);

        TenantRegisterResponse response = new TenantRegisterResponse();
        response.setOrganisationId(savedOrg.getId());
        response.setOrganisationName(savedOrg.getName());
        response.setUserId(savedUser.getId());
        response.setEmail(savedUser.getEmail());
        response.setFirstName(savedUser.getFirstName());
        response.setLastName(savedUser.getLastName());
        response.setRole(adminRole.getName());
        response.setToken(token);
        response.setExpiresIn(expiresMillis / 1000);

        Map<String, Object> model = new HashMap<>();
        model.put("firstName", savedUser.getFirstName());
        model.put("lastName", savedUser.getLastName());
        model.put("organisationName", savedOrg.getName());
        model.put("email", savedUser.getEmail());
        model.put("loginUrl", emailBaseUrl.replaceAll("/+$", "") + "/login");
        emailService.sendTemplate(savedUser.getEmail(), "Welcome to AssetIQ", "email/tenant-welcome", model);

        return response;
    }

    private String asJsonArray(Collection<String> values) {
        return values.stream()
                .map(s -> "\"" + s + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
