package com.assetiq.controllers.v1;

import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.UsageLimitService;
import com.assetiq.services.insights.BudgetBurnService;
import com.assetiq.services.insights.CostWasteService;
import com.assetiq.services.insights.EstateAnalyticsService;
import com.assetiq.services.insights.ExpiryRadarService;
import com.assetiq.services.insights.InsightSection;
import com.assetiq.services.insights.TrendService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * Operational dashboards: the questions an ITAM buyer actually asks.
 *
 * <p>Five endpoints, each answering one question a facilities or IT manager
 * would change a decision over:
 * <ul>
 *   <li>{@code /estate} — what is it worth, and where is it?</li>
 *   <li>{@code /cost-waste} — what am I paying for and not using?</li>
 *   <li>{@code /expiring} — what is about to bite me?</li>
 *   <li>{@code /spend} — where is the money going, and is it within budget?</li>
 *   <li>{@code /trends} — what has actually changed?</li>
 * </ul>
 *
 * <p><b>Two authorisation layers, on purpose.</b> The {@code @PreAuthorize} on
 * each method is the door: it decides whether the endpoint may be called at all,
 * and mirrors the existing analytics surfaces. Inside, {@link InsightSection}
 * decides what may be put in the answer, section by section, against the
 * authorities that already govern the owning controllers. A user who may not
 * list disposals does not get a disposal total; a user who may not read licences
 * sees the licence findings omitted and named in {@code withheldSections},
 * rather than shown as zero.
 *
 * <p><b>Plan gating.</b> Estate, cost-waste, spend and trends sit behind the
 * same advanced-analytics entitlement as the existing analytics endpoints.
 * {@code /expiring} deliberately does not: a customer losing a warranty because
 * their plan hid the reminder is a support incident, not an upsell.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class OperationsAnalyticsController {

    private final EstateAnalyticsService estateService;
    private final CostWasteService costWasteService;
    private final ExpiryRadarService expiryRadarService;
    private final BudgetBurnService budgetBurnService;
    private final TrendService trendService;
    private final UsageLimitService usageLimitService;
    private final OrganisationRepository organisationRepository;

    public OperationsAnalyticsController(EstateAnalyticsService estateService,
                                         CostWasteService costWasteService,
                                         ExpiryRadarService expiryRadarService,
                                         BudgetBurnService budgetBurnService,
                                         TrendService trendService,
                                         UsageLimitService usageLimitService,
                                         OrganisationRepository organisationRepository) {
        this.estateService = estateService;
        this.costWasteService = costWasteService;
        this.expiryRadarService = expiryRadarService;
        this.budgetBurnService = budgetBurnService;
        this.trendService = trendService;
        this.usageLimitService = usageLimitService;
        this.organisationRepository = organisationRepository;
    }

    /**
     * GET /api/v1/analytics/estate
     * Cost, accumulated depreciation and net book value of the on-book estate,
     * broken down by {@code groupBy} = department | location | category | status
     * | condition. Every group carries the id to link through on.
     */
    @GetMapping("/estate")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS','VIEW_REPORTS','GENERATE_REPORTS')")
    public ResponseEntity<Map<String, Object>> estate(
            @RequestParam(defaultValue = "department") String groupBy) {
        return ResponseEntity.ok(estateService.estate(requireOrgWithPlan(), groupBy, sections()));
    }

    /**
     * GET /api/v1/analytics/cost-waste
     * Assets and licences that are costing money without earning it.
     */
    @GetMapping("/cost-waste")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS','VIEW_REPORTS','GENERATE_REPORTS')")
    public ResponseEntity<Map<String, Object>> costWaste(
            @RequestParam(required = false) Integer idleDays,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(costWasteService.costWaste(requireOrgWithPlan(), idleDays, limit, sections()));
    }

    /**
     * GET /api/v1/analytics/expiring
     * Warranties, insurance, maintenance, contracts, licences and leases falling
     * due inside {@code horizonDays}, plus everything already overdue.
     *
     * <p>Not gated on plan: see the class comment.
     */
    @GetMapping("/expiring")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS','VIEW_MAINTENANCE','VIEW_CONTRACTS','VIEW_SOFTWARE_LICENSES','VIEW_REPORTS')")
    public ResponseEntity<Map<String, Object>> expiring(
            @RequestParam(required = false) Integer horizonDays,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(expiryRadarService.radar(requireOrg(), horizonDays, limit, sections()));
    }

    /**
     * GET /api/v1/analytics/spend
     * Budget burn for a window, counting commitments as well as actuals, and
     * compared against how much of the period has elapsed.
     */
    @GetMapping("/spend")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_BUDGETS','VIEW_REPORTS','GENERATE_REPORTS')")
    public ResponseEntity<Map<String, Object>> spend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(budgetBurnService.burn(requireOrgWithPlan(), from, to, sections()));
    }

    /**
     * GET /api/v1/analytics/trends
     * Recorded history for the last {@code days}. Honest about how little there
     * is: nothing is smoothed, filled in or extrapolated.
     */
    @GetMapping("/trends")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS','VIEW_REPORTS','GENERATE_REPORTS')")
    public ResponseEntity<Map<String, Object>> trends(@RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(trendService.trends(requireOrgWithPlan(), days, sections()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** The data sections this caller is allowed to be shown. */
    private Set<InsightSection> sections() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return InsightSection.grantedTo(authentication);
    }

    private Organisation requireOrg() {
        if (!TenantContext.hasOrganisationId()) {
            throw new AccessDeniedException("Tenant context is required.");
        }
        return organisationRepository.findByIdAndDeletedAtIsNull(TenantContext.getOrganisationId())
                .orElseThrow(() -> new AccessDeniedException("Organisation not found."));
    }

    private Organisation requireOrgWithPlan() {
        Organisation org = requireOrg();
        usageLimitService.assertAdvancedAnalyticsAccess(org);
        return org;
    }
}
