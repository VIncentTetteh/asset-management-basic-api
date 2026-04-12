package com.example.demo.controllers.v1;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    private final com.example.demo.services.DashboardService dashboardService;

    public DashboardController(com.example.demo.services.DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * GET /api/v1/dashboard/summary
     * Returns high-level dashboard metrics
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','VIEW_REPORTS','VIEW_ASSETS','GENERATE_REPORTS')")
    public ResponseEntity<?> getDashboardSummary(com.example.demo.models.Organisation org) {
        return ResponseEntity.ok(dashboardService.getSummary(org));
    }

    /**
     * GET /api/v1/dashboard/assets-by-status
     * Returns asset distribution by status
     */
    @GetMapping("/assets-by-status")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<?> getAssetsByStatus(com.example.demo.models.Organisation org) {
        return ResponseEntity.ok(dashboardService.getAssetsByStatus(org));
    }

    /**
     * GET /api/v1/dashboard/assets-by-department
     * Returns asset distribution by department
     */
    @GetMapping("/assets-by-department")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<?> getAssetsByDepartment(com.example.demo.models.Organisation org) {
        return ResponseEntity.ok(dashboardService.getAssetsByDepartment(org));
    }

    /**
     * GET /api/v1/dashboard/maintenance-alerts
     * Returns assets needing maintenance
     */
    @GetMapping("/maintenance-alerts")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','VIEW_MAINTENANCE','SCHEDULE_MAINTENANCE','VIEW_REPORTS')")
    public ResponseEntity<?> getMaintenanceAlerts(com.example.demo.models.Organisation org) {
        return ResponseEntity.ok(dashboardService.getMaintenanceAlerts(org));
    }

    /**
     * GET /api/v1/dashboard/depreciation-summary
     * Returns depreciation information
     */
    @GetMapping("/depreciation-summary")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','VIEW_REPORTS','VIEW_BUDGETS','VIEW_TCO')")
    public ResponseEntity<?> getDepreciationSummary(com.example.demo.models.Organisation org) {
        return ResponseEntity.ok(dashboardService.getDepreciationSummary(org));
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

