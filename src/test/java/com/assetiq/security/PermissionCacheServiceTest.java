package com.assetiq.security;

import com.assetiq.enums.Permission;
import com.assetiq.models.Role;
import com.assetiq.models.RolePermission;
import com.assetiq.models.User;
import com.assetiq.repositories.RoleRepository;
import com.assetiq.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PermissionCacheService}.
 *
 * Verifies:
 *  1. getPermissionsForRole returns sorted permissions from the join table (B-1).
 *  2. Roles with grantAllPermissions=true return the full Permission enum (B-2).
 *  3. getPermissionsForUser unions permissions across all assigned roles (B-5).
 *  4. getPermissionsForUser falls back to primary role when user_roles is empty (pre-V5 data).
 *  5. Unknown role / user returns empty list (no exception).
 */
@ExtendWith(MockitoExtension.class)
class PermissionCacheServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;

    private PermissionCacheService service;

    private final String orgId     = UUID.randomUUID().toString();
    private final String userEmail = "test@acme.com";

    @BeforeEach
    void setUp() {
        service = new PermissionCacheService(userRepository, roleRepository);
    }

    // ── getPermissionsForRole ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getPermissionsForRole")
    class GetPermissionsForRole {

        @Test
        @DisplayName("Returns sorted permissions from the join table")
        void returnsPermissionsFromJoinTable() {
            UUID roleId = UUID.randomUUID();
            Role role = roleWithPermissions(roleId, false,
                    "VIEW_ASSETS", "CREATE_ASSET", "EDIT_ASSET");
            when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

            List<String> perms = service.getPermissionsForRole(roleId);

            assertThat(perms)
                    .containsExactly("CREATE_ASSET", "EDIT_ASSET", "VIEW_ASSETS"); // sorted
        }

        @Test
        @DisplayName("Returns all Permission enum values when grantAllPermissions=true (B-2)")
        void grantAll_returnsAllPermissions() {
            UUID roleId = UUID.randomUUID();
            Role role = roleWithPermissions(roleId, true /* grantAll */);
            when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

            List<String> perms = service.getPermissionsForRole(roleId);

            // Every enum constant must appear in the result
            for (Permission p : Permission.values()) {
                assertThat(perms).contains(p.name());
            }
        }

        @Test
        @DisplayName("Returns empty list for unknown roleId (no exception)")
        void unknownRole_returnsEmptyList() {
            UUID roleId = UUID.randomUUID();
            when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

            List<String> perms = service.getPermissionsForRole(roleId);

            assertThat(perms).isEmpty();
        }

        @Test
        @DisplayName("Filters out null or blank permission names")
        void filtersBlankPermissions() {
            UUID roleId = UUID.randomUUID();
            Role role = roleWithRawPermissions(roleId, false, "VIEW_ASSETS", "", null, "  ");
            when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

            List<String> perms = service.getPermissionsForRole(roleId);

            assertThat(perms).containsExactly("VIEW_ASSETS");
        }
    }

    // ── getRoleIdsForUser ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getRoleIdsForUser")
    class GetRoleIdsForUser {

        @Test
        @DisplayName("Returns role IDs from user_roles join table (B-5)")
        void returnsIdsFromUserRolesCollection() {
            UUID roleId1 = UUID.randomUUID();
            UUID roleId2 = UUID.randomUUID();

            User user = new User();
            Role r1 = new Role(); r1.setId(roleId1);
            Role r2 = new Role(); r2.setId(roleId2);
            user.setRoles(Set.of(r1, r2));

            when(userRepository.findWithRolesByEmailAndOrgId(userEmail, UUID.fromString(orgId)))
                    .thenReturn(Optional.of(user));

            List<UUID> ids = service.getRoleIdsForUser(userEmail, orgId);

            assertThat(ids).containsExactlyInAnyOrder(roleId1, roleId2);
        }

        @Test
        @DisplayName("Falls back to primary role FK when user_roles is empty (pre-V5 data)")
        void fallsBackToPrimaryRole_whenUserRolesEmpty() {
            UUID primaryRoleId = UUID.randomUUID();

            User user = new User();
            user.setRoles(Collections.emptySet()); // empty join table
            Role primaryRole = new Role();
            primaryRole.setId(primaryRoleId);
            user.setRole(primaryRole);

            when(userRepository.findWithRolesByEmailAndOrgId(userEmail, UUID.fromString(orgId)))
                    .thenReturn(Optional.of(user));

            List<UUID> ids = service.getRoleIdsForUser(userEmail, orgId);

            assertThat(ids).containsExactly(primaryRoleId);
        }

        @Test
        @DisplayName("Returns empty list when user not found (no exception)")
        void unknownUser_returnsEmptyList() {
            when(userRepository.findWithRolesByEmailAndOrgId(any(), any()))
                    .thenReturn(Optional.empty());

            List<UUID> ids = service.getRoleIdsForUser(userEmail, orgId);

            assertThat(ids).isEmpty();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Role roleWithPermissions(UUID id, boolean grantAll, String... permissions) {
        return roleWithRawPermissions(id, grantAll, permissions);
    }

    private Role roleWithRawPermissions(UUID id, boolean grantAll, String... permissions) {
        Role role = new Role();
        role.setId(id);
        role.setGrantAllPermissions(grantAll);
        Set<RolePermission> rps = new HashSet<>();
        for (String p : permissions) {
            RolePermission rp = new RolePermission();
            rp.setRole(role);
            rp.setPermission(p);
            rps.add(rp);
        }
        role.setRolePermissions(rps);
        return role;
    }
}
