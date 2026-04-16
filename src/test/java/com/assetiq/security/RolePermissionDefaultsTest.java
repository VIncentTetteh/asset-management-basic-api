package com.assetiq.security;

import com.assetiq.enums.Permission;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RolePermissionDefaults}.
 *
 * Phase 2 / B-1: the JSON column-based helpers ({@code allPermissionsJson},
 * {@code defaultPermissionsForRole}) are deprecated and no longer exercised here.
 * The authoritative API is {@link RolePermissionDefaults#allPermissionNames()}.
 */
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
    void allPermissionNames_containsEveryPermissionEnumValue() {
        Set<String> expected = Arrays.stream(Permission.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        Set<String> actual = RolePermissionDefaults.allPermissionNames();

        assertNotNull(actual, "allPermissionNames() must not return null");
        assertFalse(actual.isEmpty(), "allPermissionNames() must not be empty");
        assertEquals(expected, actual,
                "allPermissionNames() must contain exactly every Permission enum value");
    }

    @Test
    void allPermissionNames_returnsImmutableView() {
        // Verify callers cannot corrupt the shared set.
        Set<String> names = RolePermissionDefaults.allPermissionNames();
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> names.add("FAKE_PERMISSION"),
                "allPermissionNames() should return an unmodifiable set"
        );
    }
}
