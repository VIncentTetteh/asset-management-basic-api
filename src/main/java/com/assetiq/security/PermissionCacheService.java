package com.assetiq.security;

import com.assetiq.models.RolePermission;
import com.assetiq.repositories.RoleRepository;
import com.assetiq.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Provides live permission lookups for users, backed by a two-level cache.
 *
 * <h3>Cache strategy (B-3 — targeted eviction)</h3>
 * <pre>
 *   "user-role"        keyed by "email#organisationId"  →  List&lt;UUID&gt; roleIds
 *   "role-permissions" keyed by roleId (UUID string)    →  List&lt;String&gt;
 * </pre>
 *
 * <p>When an admin updates a role's permissions only the single "role-permissions"
 * entry for that role is evicted — not the entire cache.  All other roles stay
 * cached, preventing the thundering-herd problem.
 *
 * <h3>Grant-all flag (B-2)</h3>
 * Roles with {@code grantAllPermissions = true} return every value from
 * {@link com.assetiq.enums.Permission} directly, bypassing the join table.
 *
 * <h3>Role composition (B-5)</h3>
 * A user may belong to multiple roles via the {@code user_roles} join table.
 * {@link #getPermissionsForUser} unions the permissions from every assigned role so
 * the effective permission set is always the superset of all granted roles.
 */
@Service
public class PermissionCacheService {

    private static final Logger log = LoggerFactory.getLogger(PermissionCacheService.class);

    /** Cache name for roleId → List&lt;String&gt; permissions mapping. */
    static final String ROLE_CACHE = "role-permissions";

    /** Cache name for email+orgId → List&lt;UUID&gt; roleIds mapping. */
    static final String USER_ROLE_CACHE = "user-role";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public PermissionCacheService(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the live effective permissions for the authenticated user.
     *
     * <p>Delegates to the two-level cache:
     * <ol>
     *   <li>Resolves the user's assigned role IDs (Level 1)</li>
     *   <li>Looks up permissions for each role and unions the results (Level 2)</li>
     * </ol>
     *
     * @param email          JWT subject (user email)
     * @param organisationId JWT organisationId claim (for multi-tenant disambiguation)
     * @return deduplicated list of permission strings, e.g. ["VIEW_ASSETS", "MANAGE_ROLES"]
     */
    public List<String> getPermissionsForUser(String email, String organisationId) {
        try {
            List<UUID> roleIds = getRoleIdsForUser(email, organisationId);
            if (roleIds == null || roleIds.isEmpty()) return Collections.emptyList();

            // Union permissions from every assigned role (B-5: role composition).
            return roleIds.stream()
                    .flatMap(roleId -> getPermissionsForRole(roleId).stream())
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            // Never let a cache lookup crash a request — fall back to the coarse
            // ROLE_ authority from the JWT claim.
            log.warn("[PERM] Cache lookup failed for user={} org={}: {}", email, organisationId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Evicts the permission cache for a single role.
     * Called by {@link com.assetiq.services.impl.RoleServiceImpl} whenever a role's
     * permissions are updated.  Only the affected role's entry is removed — all
     * other roles stay cached (B-3).
     */
    @CacheEvict(value = ROLE_CACHE, key = "#roleId.toString()")
    public void evictForRole(UUID roleId) {
        log.info("[PERM] Evicted permission cache for role {}", roleId);
    }

    /**
     * Evicts the user→role-list mapping for a specific user.
     * Call this whenever a user's role assignments change so the next request
     * re-resolves their role IDs from the DB.
     */
    @CacheEvict(value = USER_ROLE_CACHE, key = "#email + '#' + #organisationId")
    public void evictForUser(String email, String organisationId) {
        log.info("[PERM] Evicted user-role cache for user={} org={}", email, organisationId);
    }

    // ── Internal two-level cache ──────────────────────────────────────────────

    /**
     * Level 1: resolve all role IDs assigned to this user.
     *
     * <p>Returns IDs from the {@code user_roles} join table (B-5 role composition).
     * Cached by email+orgId.  Evict with {@link #evictForUser}.
     */
    @Cacheable(value = USER_ROLE_CACHE, key = "#email + '#' + #organisationId")
    @Transactional(readOnly = true)
    List<UUID> getRoleIdsForUser(String email, String organisationId) {
        try {
            // Use the roles-eager query when we have an orgId so the many-to-many
            // collection is loaded in a single JOIN rather than a lazy second query.
            var userOpt = (organisationId != null && !organisationId.isBlank())
                    ? userRepository.findWithRolesByEmailAndOrgId(email, UUID.fromString(organisationId))
                    : userRepository.findByEmail(email);

            return userOpt.map(user -> {
                // B-5: use the many-to-many roles set for permission resolution.
                // The roles collection is accessed within this @Transactional scope,
                // so lazy-loading works correctly.
                Set<com.assetiq.models.Role> assignedRoles = user.getRoles();
                if (assignedRoles != null && !assignedRoles.isEmpty()) {
                    return assignedRoles.stream()
                            .map(com.assetiq.models.Role::getId)
                            .collect(Collectors.toList());
                }
                // Fall back to primary role if join table is empty (e.g. pre-migration data
                // that hasn't been backfilled yet).
                if (user.getRole() != null) {
                    return List.of(user.getRole().getId());
                }
                return Collections.<UUID>emptyList();
            }).orElse(Collections.emptyList());

        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Level 2: resolve permissions for a single role.
     *
     * <p>Reads directly from the {@link com.assetiq.models.RolePermission} join table
     * (B-1: no JSON parsing).  If the role has {@code grantAllPermissions = true}
     * the full {@link com.assetiq.enums.Permission} enum is returned instead (B-2).
     *
     * Cached by roleId.  Evict with {@link #evictForRole}.
     */
    @Cacheable(value = ROLE_CACHE, key = "#roleId.toString()")
    @Transactional(readOnly = true)
    public List<String> getPermissionsForRole(UUID roleId) {
        return roleRepository.findById(roleId)
                .map(role -> {
                    // B-2: explicit flag takes priority over the stored permission rows
                    if (role.isGrantAllPermissions()) {
                        return new java.util.ArrayList<>(RolePermissionDefaults.allPermissionNames());
                    }
                    // B-1: stream from relational join table — no JSON parsing needed
                    return role.getRolePermissions().stream()
                            .map(RolePermission::getPermission)
                            .filter(p -> p != null && !p.isBlank())
                            .sorted()
                            .collect(Collectors.toList());
                })
                .orElse(Collections.emptyList());
    }
}
