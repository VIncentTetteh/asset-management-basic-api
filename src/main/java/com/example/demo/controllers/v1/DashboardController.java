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

    /**
     * GET /api/v1/dashboard/summary
     * Returns high-level dashboard metrics
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> getDashboardSummary() {
        Map<String, Object> summary = new HashMap<>();

        // Asset metrics
        summary.put("totalAssets", 150);
        summary.put("assetsInUse", 125);
        summary.put("assetsInStock", 20);
        summary.put("assetsRetired", 5);
        summary.put("assetsNeedingMaintenance", 12);
        summary.put("deprecatedAssets", 3);

        // Financial metrics
        summary.put("totalAssetValue", 750000.00);
        summary.put("totalDepreciation", 125000.00);
        summary.put("totalMaintenance", 15000.00);
        summary.put("assetTurnover", 0.15);

        // Purchase order metrics
        summary.put("pendingPurchaseOrders", 8);
        summary.put("approvedPurchaseOrders", 15);
        summary.put("totalPendingValue", 125000.00);

        // Metadata
        summary.put("lastUpdated", Instant.now().toString());
        summary.put("generatedAt", Instant.now().toString());

        return ResponseEntity.ok(summary);
    }

    /**
     * GET /api/v1/dashboard/assets-by-status
     * Returns asset distribution by status
     */
    @GetMapping("/assets-by-status")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<?> getAssetsByStatus() {
        Map<String, Object> response = new HashMap<>();

        List<Map<String, Object>> data = new ArrayList<>();
        data.add(createChartData("IN_USE", 125, 625000.00, 83.33));
        data.add(createChartData("IN_STOCK", 20, 100000.00, 13.33));
        data.add(createChartData("RETIRED", 5, 25000.00, 3.33));

        response.put("data", data);
        response.put("total", 150);
        response.put("totalValue", 750000.00);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/dashboard/assets-by-department
     * Returns asset distribution by department
     */
    @GetMapping("/assets-by-department")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<?> getAssetsByDepartment() {
        Map<String, Object> response = new HashMap<>();

        List<Map<String, Object>> data = new ArrayList<>();
        data.add(createChartData("IT Department", 45, 225000.00, 30.0));
        data.add(createChartData("HR Department", 25, 125000.00, 16.67));
        data.add(createChartData("Finance", 35, 175000.00, 23.33));
        data.add(createChartData("Operations", 45, 225000.00, 30.0));

        response.put("data", data);
        response.put("total", 150);
        response.put("totalValue", 750000.00);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/dashboard/maintenance-alerts
     * Returns assets needing maintenance
     */
    @GetMapping("/maintenance-alerts")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> getMaintenanceAlerts() {
        Map<String, Object> response = new HashMap<>();

        List<Map<String, Object>> alerts = new ArrayList<>();
        alerts.add(createAlert("Asset needs scheduled maintenance", "Dell XPS 13", "SCHEDULED", "2026-03-10"));
        alerts.add(createAlert("Asset warranty expiring", "MacBook Pro", "WARNING", "2026-03-20"));
        alerts.add(createAlert("Asset life nearing end", "HP Printer", "CRITICAL", "2026-03-15"));

        response.put("alertCount", 12);
        response.put("criticalCount", 3);
        response.put("warningCount", 4);
        response.put("scheduledCount", 5);
        response.put("alerts", alerts);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/dashboard/depreciation-summary
     * Returns depreciation information
     */
    @GetMapping("/depreciation-summary")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> getDepreciationSummary() {
        Map<String, Object> response = new HashMap<>();

        response.put("totalAssetValue", 750000.00);
        response.put("accumulatedDepreciation", 125000.00);
        response.put("netBookValue", 625000.00);
        response.put("monthlyDepreciation", 10416.67);
        response.put("averageAssetAge", 24.5);
        response.put("depreciationMethod", "STRAIGHT_LINE");
        response.put("assetsFullyDepreciated", 3);
        response.put("assetsNearEndOfLife", 8);

        return ResponseEntity.ok(response);
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

