package com.assetiq.services.ai;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * One retrievable slice of a tenant's records, paired with the authorities that
 * already govern the REST endpoint serving that slice.
 *
 * <p>The authority sets below are copied verbatim from the read mappings of the
 * owning controllers (AssetController, MaintenanceController, ...). That is
 * deliberate: the assistant must not become a second, softer door onto the same
 * data. When a controller's read authorities change, this enum changes with it,
 * and {@link AiScopeResolverTest} pins the two together.
 */
public enum AiDataSection {

    ASSETS      ("assets",              "ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_ASSETS"),
    MAINTENANCE ("maintenance records", "ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_MAINTENANCE"),
    USERS       ("people",              "ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_USERS"),
    DEPARTMENTS ("departments",         "ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_DEPARTMENTS", "MANAGE_DEPARTMENTS"),
    LOCATIONS   ("locations",           "ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_ASSETS", "VIEW_LOCATIONS", "MANAGE_LOCATIONS"),
    BUDGETS     ("budgets",             "ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_BUDGETS"),
    CONTRACTS   ("contracts",           "ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_ASSETS", "VIEW_CONTRACTS", "MANAGE_CONTRACTS"),
    LICENCES    ("software licences",   "ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_ASSETS", "VIEW_SOFTWARE_LICENSES", "MANAGE_SOFTWARE_LICENSES"),
    SUPPLIERS   ("suppliers",           "ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_ASSETS", "VIEW_SUPPLIERS", "MANAGE_SUPPLIERS"),
    DISPOSALS   ("disposals",           "ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "DISPOSE_ASSET"),
    COMPLIANCE  ("compliance controls", "ROLE_ADMIN", "ROLE_ORG_ADMIN", "VIEW_AUDIT_LOGS", "CONDUCT_AUDIT", "MANAGE_SECURITY_SETTINGS", "VIEW_COMPLIANCE", "MANAGE_COMPLIANCE"),
    RISKS       ("risk register",       "ROLE_ADMIN", "ROLE_ORG_ADMIN", "VIEW_AUDIT_LOGS", "CONDUCT_AUDIT", "MANAGE_SECURITY_SETTINGS", "VIEW_COMPLIANCE", "MANAGE_COMPLIANCE"),
    INSIGHTS    ("predictive insights", "ROLE_ADMIN", "ROLE_ORG_ADMIN", "VIEW_MAINTENANCE", "SCHEDULE_MAINTENANCE", "CONDUCT_AUDIT", "VIEW_REPORTS");

    private final String label;
    private final Set<String> authorities;

    AiDataSection(String label, String... authorities) {
        this.label = label;
        this.authorities = Set.of(authorities);
    }

    /** Human-readable name, used when telling the user what was left out. */
    public String label() {
        return label;
    }

    /** The authorities that grant read access to this slice. */
    public Set<String> authorities() {
        return authorities;
    }

    /** True when the caller holds at least one of this section's authorities. */
    public boolean isGrantedTo(Collection<String> held) {
        return authorities.stream().anyMatch(held::contains);
    }

    /**
     * The sections the given authentication may read. An unauthenticated or
     * authority-less caller gets nothing — never a default-open fallback.
     */
    public static Set<AiDataSection> grantedTo(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return EnumSet.noneOf(AiDataSection.class);
        }
        Set<String> held = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        EnumSet<AiDataSection> granted = EnumSet.noneOf(AiDataSection.class);
        Arrays.stream(values()).filter(s -> s.isGrantedTo(held)).forEach(granted::add);
        return granted;
    }
}
