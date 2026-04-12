package com.example.demo.config;

import com.example.demo.models.Role;
import com.example.demo.repositories.RoleRepository;
import com.example.demo.security.RolePermissionDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Ensures legacy admin roles always retain the full permission set.
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
    public void run(ApplicationArguments args) {
        String fullPermissions = RolePermissionDefaults.allPermissionsJson();
        long updatedCount = 0;

        for (Role role : roleRepository.findAll()) {
            if (role.getDeletedAt() != null || !RolePermissionDefaults.isAdminRoleName(role.getName())) {
                continue;
            }
            if (Objects.equals(role.getPermissions(), fullPermissions)) {
                continue;
            }

            role.setPermissions(fullPermissions);
            roleRepository.save(role);
            updatedCount++;
        }

        if (updatedCount > 0) {
            log.info("[ROLE BACKFILL] Updated {} admin role(s) with the full permission set.", updatedCount);
        } else {
            log.debug("[ROLE BACKFILL] All admin roles already have the full permission set.");
        }
    }
}
