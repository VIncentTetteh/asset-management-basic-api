package com.assetiq.config;

import com.assetiq.repositories.RoleRepository;
import com.assetiq.security.RolePermissionDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Safety-net startup runner that ensures every admin-named role carries the
 * {@code grantAllPermissions} flag.
 *
 * <p>The V3 Flyway migration ({@code V3__add_role_security_flags.sql}) sets this flag
 * for all roles whose names match the admin pattern.  This runner is a belt-and-braces
 * guard for edge cases such as roles created before the migration ran, imported via
 * SQL dump, or otherwise missed.
 *
 * <p>Since Phase 2 / B-1 the old JSON {@code permissions} column no longer exists.
 * Permission data lives in the {@code role_permission} join table; admin roles grant
 * all permissions through the {@code grantAllPermissions} flag, so no individual
 * rows need to be inserted here.
 */
@Component
public class AdminRolePermissionBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminRolePermissionBackfill.class);

    private final RoleRepository roleRepository;

    public AdminRolePermissionBackfill(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional
    @SuppressWarnings("removal") // isAdminRoleName is deprecated but intentionally used here
                                 // during the migration window — remove once fully migrated.
    public void run(ApplicationArguments args) {
        long updatedCount = 0;

        for (var role : roleRepository.findAll()) {
            if (role.getDeletedAt() != null) continue;
            if (!RolePermissionDefaults.isAdminRoleName(role.getName())) continue;
            if (role.isGrantAllPermissions()) continue;

            // Flag was missing — set it now.
            role.setGrantAllPermissions(true);
            role.setSystemRole(true);
            roleRepository.save(role);
            updatedCount++;
            log.info("[ROLE BACKFILL] Set grantAllPermissions on admin role '{}' (id={})",
                    role.getName(), role.getId());
        }

        if (updatedCount > 0) {
            log.info("[ROLE BACKFILL] Patched {} admin role(s) with grantAllPermissions=true.", updatedCount);
        } else {
            log.debug("[ROLE BACKFILL] All admin roles already have grantAllPermissions=true — nothing to do.");
        }
    }
}
