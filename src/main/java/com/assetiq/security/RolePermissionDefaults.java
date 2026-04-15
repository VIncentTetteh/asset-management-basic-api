package com.assetiq.security;

import com.assetiq.enums.Permission;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared defaults and helpers for role-based permission assignment.
 */
public final class RolePermissionDefaults {

    private static final Set<String> ALL_PERMISSION_NAMES = Arrays.stream(Permission.values())
            .map(Enum::name)
            .collect(Collectors.toCollection(LinkedHashSet::new));

    private static final String ALL_PERMISSIONS_JSON = asJsonArray(ALL_PERMISSION_NAMES);

    private RolePermissionDefaults() {
    }

    public static boolean isAdminRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return false;
        }

        String normalized = roleName.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring("ROLE_".length());
        }

        return "ADMIN".equals(normalized) || normalized.endsWith("_ADMIN");
    }

    public static String defaultPermissionsForRole(String roleName, String requestedPermissions) {
        return isAdminRoleName(roleName) ? ALL_PERMISSIONS_JSON : requestedPermissions;
    }

    public static String allPermissionsJson() {
        return ALL_PERMISSIONS_JSON;
    }

    private static String asJsonArray(Collection<String> values) {
        return values.stream()
                .map(value -> "\"" + value + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
