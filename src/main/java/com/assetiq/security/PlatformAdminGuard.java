package com.assetiq.security;

import com.assetiq.multitenancy.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Decides whether the caller is a platform operator (the vendor, not a tenant admin).
 *
 * <p>Both halves are required: the caller must belong to the configured operator
 * organisation <em>and</em> be on the email allow-list. Email alone is not enough —
 * addresses are unique only within a tenant, so anyone could register their own
 * tenant under an operator's address. With either setting blank, nobody is a
 * platform operator and every platform endpoint stays dark.
 */
@Component
public class PlatformAdminGuard {

    private final UUID operatorOrganisationId;
    private final List<String> operatorEmails;

    public PlatformAdminGuard(
            @Value("${app.platform.admin-org-id:}") String operatorOrganisationId,
            @Value("${app.platform.admin-emails:}") List<String> operatorEmails) {
        this.operatorOrganisationId = operatorOrganisationId == null || operatorOrganisationId.isBlank()
                ? null : UUID.fromString(operatorOrganisationId.trim());
        this.operatorEmails = operatorEmails == null ? List.of() : operatorEmails.stream()
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .filter(e -> !e.isEmpty())
                .toList();
    }

    public boolean isPlatformAdmin() {
        if (operatorOrganisationId == null || operatorEmails.isEmpty()) {
            return false;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return false;
        }
        return operatorOrganisationId.equals(TenantContext.getOrganisationId())
                && operatorEmails.contains(auth.getName().trim().toLowerCase(Locale.ROOT));
    }
}
