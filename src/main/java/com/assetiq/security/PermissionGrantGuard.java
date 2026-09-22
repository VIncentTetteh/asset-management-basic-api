package com.assetiq.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Privilege-escalation guard for RBAC administration: the caller may only grant
 * (by assigning a role, or by creating/editing a role) permissions they hold
 * themselves. Organisation administrators ({@code ROLE_ADMIN}/{@code ROLE_ORG_ADMIN})
 * are unrestricted. Without this, MANAGE_ROLES or MANAGE_USERS alone was enough
 * to mint or hand out the grant-all ADMIN role.
 */
public final class PermissionGrantGuard {

    private PermissionGrantGuard() {
    }

    /** Throws 403 unless the current caller holds every one of {@code permissions}. */
    public static void assertCallerHolds(Collection<String> permissions, String what) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new AccessDeniedException("No authenticated user in security context");
        }
        Set<String> held = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        if (held.contains("ROLE_ADMIN") || held.contains("ROLE_ORG_ADMIN")) return;
        Set<String> missing = new TreeSet<>(permissions);
        missing.removeAll(held);
        if (!missing.isEmpty()) {
            throw new AccessDeniedException("You cannot " + what + ": it grants permissions you do not hold ("
                    + String.join(", ", missing) + ")");
        }
    }
}
