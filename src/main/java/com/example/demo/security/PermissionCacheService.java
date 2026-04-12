package com.example.demo.security;

import com.example.demo.models.Role;
import com.example.demo.repositories.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Provides live permission lookups for users, with cache-backed results.
 *
 * <p>Each entry is keyed by {@code "email#organisationId"} in the
 * {@code "role-permissions"} cache. In production this maps to Redis
 * ({@code spring.cache.type=redis}). In development it uses Spring's
 * simple in-memory cache ({@code spring.cache.type=simple}).
 *
 * <p>When an admin updates a role's permissions, {@link #evictForRole(UUID)}
 * clears <em>all</em> entries in the cache. On their very next request the
 * filter will re-read from DB — no re-login required.
 */
@Service
public class PermissionCacheService {

    static final String CACHE_NAME = "role-permissions";

    private final UserRepository userRepository;

    public PermissionCacheService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Returns live permissions for the user identified by email + organisationId.
     * Result is cached; Spring AOP populates on miss and returns cached value on hit.
     *
     * @param email          JWT subject (user email)
     * @param organisationId JWT organisationId claim (for multi-tenant disambiguation)
     * @return list of permission strings, e.g. ["VIEW_ASSETS", "CREATE_ASSET"]
     */
    @Cacheable(value = CACHE_NAME, key = "#email + '#' + #organisationId")
    @Transactional(readOnly = true)
    public List<String> getPermissionsForUser(String email, String organisationId) {
        try {
            if (organisationId != null && !organisationId.isBlank()) {
                return userRepository
                        .findByEmailAndOrganisationId(email, UUID.fromString(organisationId))
                        .map(user -> parsePermissions(user.getRole()))
                        .orElse(Collections.emptyList());
            } else {
                return userRepository
                        .findByEmail(email)
                        .map(user -> parsePermissions(user.getRole()))
                        .orElse(Collections.emptyList());
            }
        } catch (Exception e) {
            // Never let a cache lookup break a request — return empty and let
            // the JWT-embedded role authority handle coarse-grained access.
            return Collections.emptyList();
        }
    }

    /**
     * Evicts ALL entries from the role-permissions cache.
     * Called whenever any role's permissions are updated so every affected user
     * gets a fresh DB read on their next request — changes take effect immediately.
     *
     * <p>Using {@code allEntries=true} keeps this simple and correct. Permission
     * updates are rare; clearing the full cache is safe and avoids having to
     * enumerate users per role here.
     */
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void evictForRole(UUID roleId) {
        // Spring AOP handles the cache eviction — no body needed.
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Parses the role's permissions JSON into a flat list of permission strings.
     *
     * Handles two storage formats that the frontend may produce:
     *   • JSON array:  ["VIEW_ASSETS","CREATE_ASSET"]
     *   • JSON object: {"VIEW_ASSETS":true,"CREATE_ASSET":true}  ← frontend default
     *
     * Only keys whose value is {@code true} (or any non-false truthy) are included
     * from the object format.
     */
    static List<String> parsePermissions(Role role) {
        if (role == null || role.getPermissions() == null) return Collections.emptyList();
        String raw = role.getPermissions().trim();
        if (raw.isEmpty()) return Collections.emptyList();

        try {
            if (raw.startsWith("[")) {
                // ── Array format: ["VIEW_ASSETS","CREATE_ASSET"] ──────────────
                List<String> list = MAPPER.readValue(raw, new TypeReference<List<String>>() {});
                return list.stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(String::trim)
                        .collect(Collectors.toList());

            } else if (raw.startsWith("{")) {
                // ── Object format: {"VIEW_ASSETS":true,"CREATE_ASSET":true} ───
                Map<String, Object> map = MAPPER.readValue(raw, new TypeReference<Map<String, Object>>() {});
                return map.entrySet().stream()
                        .filter(e -> Boolean.TRUE.equals(e.getValue())
                                || "true".equalsIgnoreCase(String.valueOf(e.getValue())))
                        .map(Map.Entry::getKey)
                        .filter(k -> k != null && !k.isBlank())
                        .collect(Collectors.toList());

            } else {
                // ── Legacy comma-separated fallback ───────────────────────────
                return Arrays.stream(raw.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            // Malformed JSON — return empty rather than crashing a request
            return Collections.emptyList();
        }
    }
}
