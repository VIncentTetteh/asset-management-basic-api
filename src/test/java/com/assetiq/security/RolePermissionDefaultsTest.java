package com.assetiq.security;

import com.assetiq.enums.Permission;
import com.assetiq.models.Role;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RolePermissionDefaultsTest {

    @Test
    void isAdminRoleName_recognizesAdminRoleVariants() {
        assertTrue(RolePermissionDefaults.isAdminRoleName("ADMIN"));
        assertTrue(RolePermissionDefaults.isAdminRoleName("ORG_ADMIN"));
        assertTrue(RolePermissionDefaults.isAdminRoleName("role_admin"));
        assertTrue(RolePermissionDefaults.isAdminRoleName("SUPER_ADMIN"));
        assertFalse(RolePermissionDefaults.isAdminRoleName("ASSET_MANAGER"));
        assertFalse(RolePermissionDefaults.isAdminRoleName("VIEWER"));
    }

    @Test
    void allPermissionsJson_containsEveryPermission() {
        Role role = new Role();
        role.setPermissions(RolePermissionDefaults.allPermissionsJson());

        Set<String> actualPermissions = new LinkedHashSet<>(PermissionCacheService.parsePermissions(role));
        Set<String> expectedPermissions = Arrays.stream(Permission.values())
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertEquals(expectedPermissions, actualPermissions);
    }
}
