package com.assetiq.config;

import com.assetiq.models.Department;
import com.assetiq.models.Organisation;
import com.assetiq.models.Role;
import com.assetiq.models.User;
import com.assetiq.repositories.DepartmentRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.RoleRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.services.DefaultRoleSeederService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Dev-only data seeder — creates a realistic Ghanaian demo tenant so that
 * every engineer's local instance and every staging walkthrough looks like
 * AssetIQ's target market out of the box.
 *
 * P1-10: Replaced the generic "Demo Org / IT / test.admin@example.com"
 * fixtures with a real-world Ghana scenario (Kwabenya Depot Ltd, GHS billing,
 * departments spanning Accra / Kumasi / Tamale, a BOG-style Compliance
 * officer). Anyone who lands on the dashboard immediately sees the product
 * through a Ghanaian operator's eyes.
 *
 * Activated only when spring.profiles.active includes "dev".
 */
@Component
@Order(1)
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    // ── Ghana-first demo tenant ────────────────────────────────────────────────
    private static final String ORG_NAME = "Kwabenya Depot Ltd";
    private static final String ORG_COUNTRY = "GH";
    private static final String ORG_CURRENCY = "GHS";

    /**
     * A handful of departments mirroring how a mid-market Ghanaian operation
     * actually splits cost centres. The order matters: the primary admin is
     * attached to "Operations" which is the most common landing department.
     */
    private static final List<String> DEPARTMENTS = List.of(
            "Operations",           // Accra HQ — logistics & asset owners
            "Finance & Compliance", // BOG-reporting unit, AML, audit trail
            "Kumasi Branch",        // Second biggest office in Ashanti region
            "Tamale Branch",        // Northern distribution hub
            "IT & Security"         // Platform administrators
    );

    private static final String PRIMARY_DEPARTMENT = "Operations";
    private static final String ROLE_NAME = "ADMIN";

    // Primary seed user — Ghanaian name, .com.gh email, Ghana mobile format
    private static final String USER_EMAIL = "ama.boateng@kwabenya.com.gh";
    private static final String USER_FIRST_NAME = "Ama";
    private static final String USER_LAST_NAME = "Boateng";
    private static final String USER_EMPLOYEE_ID = "KDL-001";
    private static final String USER_JOB_TITLE = "Operations Director";
    private static final String USER_TEMP_PASSWORD = "Password123!"; // will be hashed

    private final OrganisationRepository organisationRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DefaultRoleSeederService defaultRoleSeederService;

    public DevDataSeeder(OrganisationRepository organisationRepository,
            DepartmentRepository departmentRepository,
            RoleRepository roleRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            DefaultRoleSeederService defaultRoleSeederService) {
        this.organisationRepository = organisationRepository;
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.defaultRoleSeederService = defaultRoleSeederService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("[DEV SEED] Starting Ghana-first dev data seeding…");

        // 1) Organisation — Kwabenya Depot Ltd
        Organisation org = organisationRepository
                .findByNameIgnoreCaseAndDeletedAtIsNull(ORG_NAME)
                .orElseGet(() -> {
                    Organisation o = new Organisation();
                    o.setName(ORG_NAME);
                    // setCountry / setBillingCurrency added in P1-1; guard-wrap
                    // via reflection so we stay resilient if a future refactor
                    // renames the fields. Seeder should never crash the boot.
                    trySet(o, "setCountry", ORG_COUNTRY);
                    trySet(o, "setBillingCurrency", ORG_CURRENCY);
                    log.info("[DEV SEED] Creating organisation: {} ({}, {})", ORG_NAME, ORG_COUNTRY, ORG_CURRENCY);
                    return organisationRepository.save(o);
                });

        // 2) Departments — realistic Ghana-first cost centres
        Department primaryDept = null;
        for (String deptName : DEPARTMENTS) {
            Department d = departmentRepository
                    .findByNameIgnoreCaseAndOrganisationAndDeletedAtIsNull(deptName, org)
                    .orElseGet(() -> {
                        Department fresh = new Department();
                        fresh.setName(deptName);
                        fresh.setOrganisation(org);
                        log.info("[DEV SEED] Creating department: {} (org={})", deptName, org.getName());
                        return departmentRepository.save(fresh);
                    });
            if (PRIMARY_DEPARTMENT.equalsIgnoreCase(deptName)) {
                primaryDept = d;
            }
        }
        if (primaryDept == null) {
            throw new IllegalStateException(
                    "[DEV SEED] Primary department '" + PRIMARY_DEPARTMENT + "' was not created.");
        }

        // 3) Seed all standard platform roles (idempotent — skips existing ones)
        defaultRoleSeederService.seedRolesForOrganisation(org);

        // Retrieve the ADMIN role that was just seeded (or already existed)
        Role role = roleRepository
                .findByNameAndOrganisationAndDeletedAtIsNull(ROLE_NAME, org)
                .orElseThrow(() -> new IllegalStateException(
                        "[DEV SEED] ADMIN role not found after seeding — this should not happen"));

        // 4) User — Ghanaian operations director
        Optional<User> existingByEmail = userRepository.findByEmailAndOrganisationId(USER_EMAIL, getId(org));
        Optional<User> existingByEmpId = userRepository.findByEmployeeId(USER_EMPLOYEE_ID);

        if (existingByEmail.isPresent() || existingByEmpId.isPresent()) {
            User user = existingByEmail.orElseGet(existingByEmpId::get);
            log.info("[DEV SEED] Demo user already exists (email={} or empId={})", user.getEmail(),
                    user.getEmployeeId());

            boolean updated = false;
            if (user.getRole() == null || !getId(user.getRole()).equals(getId(role))) {
                user.setRole(role);
                updated = true;
            }
            if (user.getDepartment() == null || !getId(user.getDepartment()).equals(getId(primaryDept))) {
                user.setDepartment(primaryDept);
                updated = true;
            }
            if (user.getOrganisation() == null || !getId(user.getOrganisation()).equals(getId(org))) {
                user.setOrganisation(org);
                updated = true;
            }
            if (updated) {
                userRepository.save(user);
                log.info("[DEV SEED] Ensured associations for existing demo user are up to date.");
            }
        } else {
            User user = new User();
            user.setFirstName(USER_FIRST_NAME);
            user.setLastName(USER_LAST_NAME);
            user.setEmail(USER_EMAIL);
            user.setJobTitle(USER_JOB_TITLE);
            user.setEmployeeId(USER_EMPLOYEE_ID);
            user.setPasswordHash(passwordEncoder.encode(USER_TEMP_PASSWORD));
            user.setRole(role);
            user.setOrganisation(org);
            user.setDepartment(primaryDept);

            userRepository.save(user);
            log.info("[DEV SEED] Created demo user: {} (password: {})", USER_EMAIL, USER_TEMP_PASSWORD);
        }

        log.info("[DEV SEED] Ghana-first demo tenant ready — org={}, currency={}, departments={}",
                ORG_NAME, ORG_CURRENCY, DEPARTMENTS.size());
    }

    private static UUID getId(Object entity) {
        try {
            java.lang.reflect.Method m = entity.getClass().getMethod("getId");
            Object id = m.invoke(entity);
            return (UUID) id;
        } catch (Exception e) {
            throw new IllegalStateException("Entity does not expose getId(): " + entity.getClass(), e);
        }
    }

    /**
     * Best-effort setter invocation — used only for optional fields
     * ({@code country}, {@code billingCurrency}) that may not exist on older
     * branches. We swallow reflection errors so the seeder is forward- and
     * backward-compatible with the Organisation schema.
     */
    private static void trySet(Object entity, String setterName, String value) {
        try {
            java.lang.reflect.Method setter = entity.getClass().getMethod(setterName, String.class);
            setter.invoke(entity, value);
        } catch (NoSuchMethodException e) {
            log.debug("[DEV SEED] Optional setter {} not present — skipping.", setterName);
        } catch (Exception e) {
            log.warn("[DEV SEED] Could not set {} = {} via {}", setterName, value, e.getMessage());
        }
    }
}
