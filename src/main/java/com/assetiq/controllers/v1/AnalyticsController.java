package com.assetiq.controllers.v1;

import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.AnalyticsService;
import com.assetiq.services.UsageLimitService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private static final int MAX_TREND_MONTHS = 60;

    private final AnalyticsService analyticsService;
    private final UsageLimitService usageLimitService;
    private final OrganisationRepository organisationRepository;

    public AnalyticsController(AnalyticsService analyticsService,
                               UsageLimitService usageLimitService,
                               OrganisationRepository organisationRepository) {
        this.analyticsService = analyticsService;
        this.usageLimitService = usageLimitService;
        this.organisationRepository = organisationRepository;
    }

    /**
     * GET /api/v1/analytics/assets
     * Asset breakdown by status, department, or condition.
     * Query params:
     *   period  — display label only (week | month | quarter | year), default "month"
     *   groupBy — status | department | condition, default "status"
     */
    @GetMapping("/assets")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_REPORTS','GENERATE_REPORTS')")
    public ResponseEntity<Map<String, Object>> getAssetAnalytics(
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(defaultValue = "status") String groupBy) {
        Organisation org = requireOrg();
        return ResponseEntity.ok(analyticsService.getAssetAnalytics(period, groupBy, org));
    }

    /**
     * GET /api/v1/analytics/financial
     * Asset values, depreciation totals, and per-category breakdown.
     */
    @GetMapping("/financial")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_REPORTS','GENERATE_REPORTS')")
    public ResponseEntity<Map<String, Object>> getFinancialAnalytics(
            @RequestParam(defaultValue = "month") String period) {
        Organisation org = requireOrg();
        return ResponseEntity.ok(analyticsService.getFinancialAnalytics(period, org));
    }

    /**
     * GET /api/v1/analytics/purchase-orders
     * PO counts by status, totals, averages, and top suppliers.
     */
    @GetMapping("/purchase-orders")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_REPORTS','GENERATE_REPORTS')")
    public ResponseEntity<Map<String, Object>> getPurchaseOrderAnalytics(
            @RequestParam(defaultValue = "month") String period) {
        Organisation org = requireOrg();
        return ResponseEntity.ok(analyticsService.getPurchaseOrderAnalytics(period, org));
    }

    /**
     * GET /api/v1/analytics/maintenance
     * Maintenance records: totals, cost, overdue assets, breakdown by type.
     */
    @GetMapping("/maintenance")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_REPORTS','GENERATE_REPORTS')")
    public ResponseEntity<Map<String, Object>> getMaintenanceAnalytics() {
        Organisation org = requireOrg();
        return ResponseEntity.ok(analyticsService.getMaintenanceAnalytics(org));
    }

    /**
     * GET /api/v1/analytics/depreciation-trends
     * Month-by-month book value and depreciation charges for the last N months (max 60).
     */
    @GetMapping("/depreciation-trends")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_REPORTS','GENERATE_REPORTS','VIEW_DEPRECIATION','MANAGE_DEPRECIATION')")
    public ResponseEntity<Map<String, Object>> getDepreciationTrends(
            @RequestParam(defaultValue = "12") int months) {
        if (months < 1 || months > MAX_TREND_MONTHS) {
            throw new IllegalArgumentException("months must be between 1 and " + MAX_TREND_MONTHS);
        }
        Organisation org = requireOrg();
        return ResponseEntity.ok(analyticsService.getDepreciationTrends(months, org));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Organisation requireOrg() {
        if (!TenantContext.hasOrganisationId()) {
            throw new org.springframework.security.access.AccessDeniedException("Tenant context is required.");
        }
        Organisation org = organisationRepository.findByIdAndDeletedAtIsNull(TenantContext.getOrganisationId())
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Organisation not found."));
        usageLimitService.assertAdvancedAnalyticsAccess(org);
        return org;
    }
}
