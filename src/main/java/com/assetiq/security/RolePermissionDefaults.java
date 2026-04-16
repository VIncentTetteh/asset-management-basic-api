package com.assetiq.security;

import com.assetiq.enums.Permission;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared defaults and helpers for role-based permission assignment.
 *
 * <p><b>Admin detection</b> is now driven by the {@code grantAllPermissions} flag on
 * the {@link com.assetiq.models.Role} entity instead of role-name string matching.
 * The legacy {@link #isAdminRoleName(String)} method is kept for backward-compatibility
 * during the migration window but should not be used for new code.
 */
public final class RolePermissionDefaults {

    // Unmodifiable so callers cannot corrupt the shared canonical set.
    // Static initializer used instead of a field initializer to give javac an
    // explicit LinkedHashSet<String> type, avoiding type-inference ambiguity in
    // Collections.unmodifiableSet() on newer javac versions (javac 21+).
    private static final Set<String> ALL_PERMISSION_NAMES;
    static {
        LinkedHashSet<String> tmp = Arrays.stream(Permission.values())
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        ALL_PERMISSION_NAMES = Collections.unmodifiableSet(tmp);
    }

    private static final String ALL_PERMISSIONS_JSON = asJsonArray(ALL_PERMISSION_NAMES);

    private RolePermissionDefaults() {
    }

    /**
     * Returns all permissions as a JSON array string.
     *
     * @deprecated Phase 2 / B-1 — permissions are now stored in the
     *             {@code role_permission} join table, not as JSON.
     *             Use {@link #allPermissionNames()} instead.
     */
    @Deprecated(since = "phase-2", forRemoval = true)
    public static String allPermissionsJson() {
        return ALL_PERMISSIONS_JSON;
    }

    /**
     * Returns all permission names as an ordered set — used by
     * {@link PermissionCacheService} when resolving a grant-all role.
     */
    public static Set<String> allPermissionNames() {
        return ALL_PERMISSION_NAMES;
    }

    /**
     * Returns the effective JSON permissions string.
     *
     * @deprecated Phase 2 / B-1 — permissions are now stored relationally.
     *             Use {@link #allPermissionNames()} to get the full set when
     *             {@code grantAll} is true; pass the list directly to
     *             {@code RoleServiceImpl.buildPermissions()} otherwise.
     */
    @Deprecated(since = "phase-2", forRemoval = true)
    public static String defaultPermissionsForRole(boolean grantAll, String requestedPermissions) {
        return grantAll ? ALL_PERMISSIONS_JSON : requestedPermissions;
    }

    /**
     * @deprecated Use the {@code grantAllPermissions} flag on the Role entity instead.
     *             This method remains only for backward-compatibility during the V3 migration.
     */
    @Deprecated(since = "phase-1", forRemoval = true)
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

    /**
     * @deprecated Use {@link #defaultPermissionsForRole(boolean, String)} instead.
     */
    @Deprecated(since = "phase-1", forRemoval = true)
    public static String defaultPermissionsForRole(String roleName, String requestedPermissions) {
        return isAdminRoleName(roleName) ? ALL_PERMISSIONS_JSON : requestedPermissions;
    }

    private static String asJsonArray(Collection<String> values) {
        return values.stream()
                .map(value -> "\"" + value + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
