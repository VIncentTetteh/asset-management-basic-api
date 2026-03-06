package com.example.demo.controllers.v1;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * Analytics Controller
 * Provides detailed analytics and reporting for enterprise insights
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    /**
     * GET /api/v1/analytics/assets
     * Returns detailed asset analytics
     */
    @GetMapping("/assets")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> getAssetAnalytics(
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(defaultValue = "status") String groupBy) {

        Map<String, Object> response = new HashMap<>();
        response.put("period", period);
        response.put("groupBy", groupBy);

        List<Map<String, Object>> data = new ArrayList<>();
        if (groupBy.equals("status")) {
            data.add(createAnalyticsEntry("IN_USE", 125, 625000.00));
            data.add(createAnalyticsEntry("IN_STOCK", 20, 100000.00));
            data.add(createAnalyticsEntry("RETIRED", 5, 25000.00));
        } else if (groupBy.equals("department")) {
            data.add(createAnalyticsEntry("IT", 45, 225000.00));
            data.add(createAnalyticsEntry("HR", 25, 125000.00));
            data.add(createAnalyticsEntry("Finance", 35, 175000.00));
            data.add(createAnalyticsEntry("Operations", 45, 225000.00));
        } else if (groupBy.equals("condition")) {
            data.add(createAnalyticsEntry("GOOD", 100, 500000.00));
            data.add(createAnalyticsEntry("EXCELLENT", 40, 200000.00));
            data.add(createAnalyticsEntry("FAIR", 10, 50000.00));
        }

        response.put("data", data);
        response.put("total", 150);
        response.put("totalValue", 750000.00);
        response.put("generatedAt", Instant.now().toString());

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/analytics/financial
     * Returns financial analytics including depreciation
     */
    @GetMapping("/financial")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> getFinancialAnalytics(
            @RequestParam(defaultValue = "month") String period) {

        Map<String, Object> response = new HashMap<>();
        response.put("period", period);

        // Financial metrics
        response.put("totalAssetValue", 750000.00);
        response.put("totalDepreciation", 125000.00);
        response.put("netBookValue", 625000.00);
        response.put("totalAcquisition", 25000.00);
        response.put("totalDisposal", 500.00);
        response.put("totalMaintenance", 3200.00);

        // Key metrics
        response.put("monthlyDepreciation", 10416.67);
        response.put("assetTurnover", 0.15);
        response.put("averageAssetAge", 24.5);
        response.put("depreciationMethod", "STRAIGHT_LINE");
        response.put("assetsFullyDepreciated", 3);

        // Breakdown by category
        Map<String, Object> byCategory = new HashMap<>();
        byCategory.put("IT Equipment", createCategoryMetrics("IT Equipment", 45, 225000.00, 37500.00));
        byCategory.put("Furniture", createCategoryMetrics("Furniture", 35, 87500.00, 14583.33));
        byCategory.put("Vehicles", createCategoryMetrics("Vehicles", 25, 250000.00, 41666.67));
        byCategory.put("Other", createCategoryMetrics("Other", 45, 187500.00, 31250.00));

        response.put("breakdown", Map.of("byCategory", byCategory));
        response.put("generatedAt", Instant.now().toString());

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/analytics/purchase-orders
     * Returns PO analytics
     */
    @GetMapping("/purchase-orders")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> getPurchaseOrderAnalytics(
            @RequestParam(defaultValue = "month") String period) {

        Map<String, Object> response = new HashMap<>();
        response.put("period", period);

        response.put("totalPOs", 50);
        response.put("draftPOs", 8);
        response.put("approvedPOs", 35);
        response.put("rejectedPOs", 7);

        response.put("totalPOValue", 425000.00);
        response.put("averagePOValue", 8500.00);
        response.put("largestPO", 35000.00);
        response.put("smallestPO", 250.00);

        response.put("averageApprovalTime", 2.5);
        response.put("averageDeliveryTime", 14);

        List<Map<String, Object>> topSuppliers = new ArrayList<>();
        topSuppliers.add(Map.of("supplier", "Acme Corp", "poCount", 12, "totalValue", 85000.00));
        topSuppliers.add(Map.of("supplier", "Tech Supplies Inc", "poCount", 10, "totalValue", 75000.00));
        topSuppliers.add(Map.of("supplier", "Office Furniture Ltd", "poCount", 8, "totalValue", 65000.00));

        response.put("topSuppliers", topSuppliers);
        response.put("generatedAt", Instant.now().toString());

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/analytics/maintenance
     * Returns maintenance analytics
     */
    @GetMapping("/maintenance")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> getMaintenanceAnalytics() {

        Map<String, Object> response = new HashMap<>();

        response.put("totalMaintenanceRecords", 145);
        response.put("assetsMaintained", 98);
        response.put("totalMaintenanceCost", 35000.00);
        response.put("averageMaintenanceCost", 241.38);
        response.put("assetsNeedingMaintenance", 12);

        response.put("preventiveMaintenanceCount", 120);
        response.put("correctiveMaintenanceCount", 25);

        response.put("maintenanceCostByType", Map.of(
            "Hardware", 15000.00,
            "Software", 8000.00,
            "Repair", 12000.00
        ));

        response.put("generatedAt", Instant.now().toString());

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/analytics/depreciation-trends
     * Returns depreciation trend analysis
     */
    @GetMapping("/depreciation-trends")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> getDepreciationTrends(
            @RequestParam(defaultValue = "12") int months) {

        Map<String, Object> response = new HashMap<>();
        response.put("period", months + " months");

        List<Map<String, Object>> trends = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            trends.add(Map.of(
                "month", String.format("2025-%02d", (i + 1)),
                "totalValue", 750000.00 - (i * 10416.67),
                "depreciation", 10416.67,
                "assetsRetired", i / 3
            ));
        }

        response.put("trends", trends);
        response.put("totalDepreciationOverPeriod", 125000.00);
        response.put("generatedAt", Instant.now().toString());

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> createAnalyticsEntry(String name, int count, double value) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("name", name);
        entry.put("count", count);
        entry.put("value", value);
        entry.put("percentage", (count / 150.0) * 100);
        return entry;
    }

    private Map<String, Object> createCategoryMetrics(String category, int count, double value, double depreciation) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("category", category);
        metrics.put("count", count);
        metrics.put("value", value);
        metrics.put("monthlyDepreciation", depreciation);
        return metrics;
    }
}

