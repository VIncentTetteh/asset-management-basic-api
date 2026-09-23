package com.assetiq.imports;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who may import what.
 *
 * <p>Importing is a bulk write, so it requires the same authority the type's own write
 * endpoints require — see {@link ImportEntityType#writeAuthorities()}. The point is
 * that the permissions are <em>per type</em>: somebody trusted with the supplier list
 * does not thereby get to create three thousand employee records.</p>
 *
 * <p>Exposed as a Spring bean named {@code importPermissions} so
 * {@code @PreAuthorize("@importPermissions.canImport(#type)")} can use it with the
 * path variable, which a static annotation cannot do.</p>
 */
@Component("importPermissions")
public class ImportPermissions {

    /** For {@code @PreAuthorize}: takes the raw URL segment, unknown types deny. */
    public boolean canImport(String typeSlug) {
        return ImportEntityType.fromSlug(typeSlug).map(this::isPermitted).orElse(false);
    }

    public boolean isPermitted(ImportEntityType type) {
        Set<String> held = currentAuthorities();
        if (held.stream().anyMatch(ImportEntityType.ADMIN_AUTHORITIES::contains)) {
            return true;
        }
        return held.stream().anyMatch(type.writeAuthorities()::contains);
    }

    /**
     * Enforce outside the controller layer — the job service re-checks on commit and on
     * status reads, so an entity type cannot be smuggled past the URL's check.
     */
    public void require(ImportEntityType type) {
        if (!isPermitted(type)) {
            throw new AccessDeniedException(
                    "You do not have permission to import " + type.label().toLowerCase(java.util.Locale.ROOT));
        }
    }

    private Set<String> currentAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
