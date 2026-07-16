package com.assetiq.controllers.v1;

import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.OrganisationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * Dashboard & Analytics Controller
 * Provides executive dashboards, analytics, and metrics
 * Required for enterprise SaaS products
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final com.assetiq.services.DashboardService dashboardService;
    private final OrganisationRepository organisationRepository;

    public DashboardController(com.assetiq.services.DashboardService dashboardService,
                                OrganisationRepository organisationRepository) {
        this.dashboardService = dashboardService;
        this.organisationRepository = organisationRepository;
    }

    /**
     * GET /api/v1/dashboard/summary
     * Returns high-level dashboard metrics
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','VIEW_REPORTS','VIEW_ASSETS','GENERATE_REPORTS')")
    public ResponseEntity<?> getDashboardSummary() {
        return ResponseEntity.ok(dashboardService.getSummary(requireOrg()));
    }

    /**
     * GET /api/v1/dashboard/assets-by-status
     * Returns asset distribution by status
     */
    @GetMapping("/assets-by-status")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<?> getAssetsByStatus() {
        return ResponseEntity.ok(dashboardService.getAssetsByStatus(requireOrg()));
    }

    /**
     * GET /api/v1/dashboard/assets-by-department
     * Returns asset distribution by department
     */
    @GetMapping("/assets-by-department")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<?> getAssetsByDepartment() {
        return ResponseEntity.ok(dashboardService.getAssetsByDepartment(requireOrg()));
    }

    /**
     * GET /api/v1/dashboard/maintenance-alerts
     * Returns assets needing maintenance
     */
    @GetMapping("/maintenance-alerts")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','VIEW_MAINTENANCE','SCHEDULE_MAINTENANCE','VIEW_REPORTS')")
    public ResponseEntity<?> getMaintenanceAlerts() {
        return ResponseEntity.ok(dashboardService.getMaintenanceAlerts(requireOrg()));
    }

    /**
     * GET /api/v1/dashboard/depreciation-summary
     * Returns depreciation information
     */
    @GetMapping("/depreciation-summary")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','VIEW_REPORTS','VIEW_BUDGETS','VIEW_TCO')")
    public ResponseEntity<?> getDepreciationSummary() {
        return ResponseEntity.ok(dashboardService.getDepreciationSummary(requireOrg()));
    }

    private Organisation requireOrg() {
        if (!TenantContext.hasOrganisationId()) {
            throw new AccessDeniedException("Tenant context is required.");
        }
        return organisationRepository.findByIdAndDeletedAtIsNull(TenantContext.getOrganisationId())
                .orElseThrow(() -> new AccessDeniedException("Organisation not found."));
    }

    private Map<String, Object> createChartData(String name, int count, double value, double percentage) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("count", count);
        data.put("value", value);
        data.put("percentage", percentage);
        return data;
    }

    private Map<String, Object> createAlert(String message, String assetName, String severity, String dueDate) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("message", message);
        alert.put("assetName", assetName);
        alert.put("severity", severity);
        alert.put("dueDate", dueDate);
        return alert;
    }
}

