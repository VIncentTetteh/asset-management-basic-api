package com.example.demo.config;

import com.example.demo.models.Department;
import com.example.demo.models.Organisation;
import com.example.demo.models.Role;
import com.example.demo.models.User;
import com.example.demo.repositories.DepartmentRepository;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.repositories.RoleRepository;
import com.example.demo.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Dev-only data seeder to create a test organisation, department, role and
 * user.
 *
 * Activated only when spring.profiles.active includes "dev".
 */
@Component
@Order(1)
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    // Defaults – adjust via code if you need different values
    private static final String ORG_NAME = "Demo Org";
    private static final String DEPT_NAME = "IT";
    private static final String ROLE_NAME = "ADMIN";

    private static final String USER_EMAIL = "test.admin@example.com";
    private static final String USER_FIRST_NAME = "Test";
    private static final String USER_LAST_NAME = "Admin";
    private static final String USER_EMPLOYEE_ID = "EMP-TEST-ADMIN";
    private static final String USER_JOB_TITLE = "Administrator";
    private static final String USER_TEMP_PASSWORD = "Password123!"; // will be hashed

    private final OrganisationRepository organisationRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(OrganisationRepository organisationRepository,
            DepartmentRepository departmentRepository,
            RoleRepository roleRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.organisationRepository = organisationRepository;
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("[DEV SEED] Starting dev data seeding...");

        // 1) Organisation
        Organisation org = organisationRepository
                .findByNameIgnoreCaseAndDeletedAtIsNull(ORG_NAME)
                .orElseGet(() -> {
                    Organisation o = new Organisation();
                    o.setName(ORG_NAME);
                    log.info("[DEV SEED] Creating organisation: {}", ORG_NAME);
                    return organisationRepository.save(o);
                });

        // 2) Department (scoped to org)
        Department dept = departmentRepository
                .findByNameIgnoreCaseAndOrganisationAndDeletedAtIsNull(DEPT_NAME, org)
                .orElseGet(() -> {
                    Department d = new Department();
                    d.setName(DEPT_NAME);
                    d.setOrganisation(org);
                    log.info("[DEV SEED] Creating department: {} (org={})", DEPT_NAME, org.getName());
                    return departmentRepository.save(d);
                });

        // 3) Role (scoped to org)
        Role role = roleRepository
                .findByNameAndOrganisationId(ROLE_NAME, getId(org))
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName(ROLE_NAME);
                    r.setDescription("Administrator role with full access (dev-only seed)");
                    r.setPermissions("[\"ALL\"]");
                    r.setOrganisation(org);
                    log.info("[DEV SEED] Creating role: {} (org={})", ROLE_NAME, org.getName());
                    return roleRepository.save(r);
                });

        // 4) User (email unique per org, employeeId globally unique)
        Optional<User> existingByEmail = userRepository.findByEmailAndOrganisationId(USER_EMAIL, getId(org));
        Optional<User> existingByEmpId = userRepository.findByEmployeeId(USER_EMPLOYEE_ID);

        if (existingByEmail.isPresent() || existingByEmpId.isPresent()) {
            User user = existingByEmail.orElseGet(existingByEmpId::get);
            log.info("[DEV SEED] Test user already exists (email={} or empId={})", user.getEmail(),
                    user.getEmployeeId());

            // Optionally ensure associations are set
            boolean updated = false;
            if (user.getRole() == null || !getId(user.getRole()).equals(getId(role))) {
                user.setRole(role);
                updated = true;
            }
            if (user.getDepartment() == null || !getId(user.getDepartment()).equals(getId(dept))) {
                user.setDepartment(dept);
                updated = true;
            }
            if (user.getOrganisation() == null || !getId(user.getOrganisation()).equals(getId(org))) {
                user.setOrganisation(org);
                updated = true;
            }
            if (updated) {
                userRepository.save(user);
                log.info("[DEV SEED] Ensured associations for existing test user are up to date.");
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
            user.setDepartment(dept);

            userRepository.save(user);
            log.info("[DEV SEED] Created test user: {} (password: {})", USER_EMAIL, USER_TEMP_PASSWORD);
        }

        log.info("[DEV SEED] Completed dev data seeding.");
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
}
