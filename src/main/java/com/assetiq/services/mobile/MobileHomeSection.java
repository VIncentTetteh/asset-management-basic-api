package com.assetiq.services.mobile;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One part of the mobile Home payload, paired with the authorities that already
 * govern the REST endpoint owning that data.
 *
 * <p>Same rule as {@link com.assetiq.services.insights.InsightSection}: an
 * aggregate must not be a softer door onto data the caller cannot open
 * directly. Each authority set is copied verbatim from the owning endpoint's
 * {@code @PreAuthorize}, and {@code MobileHomeSectionTest} pins them.
 */
public enum MobileHomeSection {

    /** Portfolio counts and the recently-updated list — AssetController#list. */
    ASSETS              ("ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_ASSETS"),

    /** Overdue maintenance — MaintenanceController#list. */
    MAINTENANCE         ("ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_MAINTENANCE"),

    /**
     * Transfers awaiting approval — AssetTransferController#approve, not #list:
     * the count is "approvals you could action", so it follows who may approve.
     */
    TRANSFER_APPROVALS  ("ROLE_ADMIN", "ROLE_ORG_ADMIN", "TRANSFER_ASSET"),

    /** Overdue checkouts — CheckoutController#listOverdue. */
    CHECKOUTS           ("ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "CHECKOUT_ASSET", "VIEW_ASSETS"),

    /**
     * Budget utilisation — BudgetController#getSummary, not #list: the figure is
     * an org-wide money total, which is exactly what the summary guards.
     */
    BUDGETS             ("ROLE_ADMIN", "ROLE_ORG_ADMIN", "VIEW_BUDGETS");

    private final Set<String> authorities;

    MobileHomeSection(String... authorities) {
        this.authorities = Set.of(authorities);
    }

    /** The authorities that grant read access to this section. */
    public Set<String> authorities() {
        return authorities;
    }

    public boolean isGrantedTo(Collection<String> held) {
        return authorities.stream().anyMatch(held::contains);
    }

    /**
     * The sections the given authentication may read. An unauthenticated or
     * authority-less caller gets nothing — never a default-open fallback.
     */
    public static Set<MobileHomeSection> grantedTo(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return EnumSet.noneOf(MobileHomeSection.class);
        }
        Set<String> held = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
        EnumSet<MobileHomeSection> granted = EnumSet.noneOf(MobileHomeSection.class);
        Arrays.stream(values()).filter(s -> s.isGrantedTo(held)).forEach(granted::add);
        return granted;
    }
}
