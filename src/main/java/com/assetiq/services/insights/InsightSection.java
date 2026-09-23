package com.assetiq.services.insights;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One slice of a dashboard answer, paired with the authorities that already
 * govern the REST endpoint owning that data.
 *
 * <p>Same rule as {@link com.assetiq.services.ai.AiDataSection}: a dashboard
 * must not become a second, softer door onto records the caller cannot open
 * directly. A user who may not list disposals must not be handed a disposal
 * total, however aggregated. The authority sets below are copied verbatim from
 * the read mappings of the owning controllers, and
 * {@code InsightSectionAuthorityTest} pins the two together.
 *
 * <p>Aggregates never fail closed-with-an-error: a section the caller cannot
 * see is simply omitted and named in {@code withheldSections}, so the UI can
 * say "you do not have access to licence data" rather than silently showing a
 * zero that looks like good news.
 */
public enum InsightSection {

    /** Counts and identity of assets — AssetController#list. */
    ASSETS      ("assets",              "ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_ASSETS"),

    /** Money on assets: cost, book value, depreciation — AnalyticsController#financial. */
    VALUATION   ("asset valuations",    "ROLE_ADMIN", "ROLE_ORG_ADMIN", "VIEW_REPORTS", "GENERATE_REPORTS",
                                        "VIEW_DEPRECIATION", "MANAGE_DEPRECIATION", "VIEW_TCO"),

    /** MaintenanceController#list. */
    MAINTENANCE ("maintenance records", "ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_MAINTENANCE"),

    /** SoftwareLicenseController#list. */
    LICENCES    ("software licences",   "ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_ASSETS",
                                        "VIEW_SOFTWARE_LICENSES", "MANAGE_SOFTWARE_LICENSES"),

    /** ContractController#list. */
    CONTRACTS   ("contracts",           "ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_ASSETS",
                                        "VIEW_CONTRACTS", "MANAGE_CONTRACTS"),

    /** LeaseRecordController#list. */
    LEASES      ("leases",              "ROLE_ORG_ADMIN", "ROLE_ADMIN", "ROLE_USER", "MANAGE_LEASES", "VIEW_CONTRACTS"),

    /** BudgetController#list. */
    BUDGETS     ("budgets",             "ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_BUDGETS"),

    /** DisposalController#list. */
    DISPOSALS   ("disposals",           "ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "DISPOSE_ASSET"),

    /** DepartmentController#list — needed to name a department in a breakdown. */
    DEPARTMENTS ("departments",         "ROLE_ORG_ADMIN", "ROLE_USER", "ROLE_ADMIN", "VIEW_DEPARTMENTS", "MANAGE_DEPARTMENTS"),

    /** LocationController#list — needed to name a location in a breakdown. */
    LOCATIONS   ("locations",           "ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER", "VIEW_ASSETS",
                                        "VIEW_LOCATIONS", "MANAGE_LOCATIONS");

    private final String label;
    private final Set<String> authorities;

    InsightSection(String label, String... authorities) {
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

    public boolean isGrantedTo(Collection<String> held) {
        return authorities.stream().anyMatch(held::contains);
    }

    /**
     * The sections the given authentication may read. An unauthenticated or
     * authority-less caller gets nothing — never a default-open fallback.
     */
    public static Set<InsightSection> grantedTo(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return EnumSet.noneOf(InsightSection.class);
        }
        Set<String> held = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
        EnumSet<InsightSection> granted = EnumSet.noneOf(InsightSection.class);
        Arrays.stream(values()).filter(s -> s.isGrantedTo(held)).forEach(granted::add);
        return granted;
    }
}
