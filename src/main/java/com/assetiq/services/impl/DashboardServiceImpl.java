package com.assetiq.services.impl;

import com.assetiq.enums.*;
import com.assetiq.models.*;
import com.assetiq.repositories.*;
import com.assetiq.services.DashboardService;
import com.assetiq.services.finance.PortfolioValuation;
import com.assetiq.services.money.CurrencyConversion;
import com.assetiq.services.money.MoneyAccumulator;
import com.assetiq.services.money.MoneyAggregator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dashboard aggregates. Every money figure is converted into the tenant base
 * currency through {@link MoneyAggregator}; responses carry {@code currency},
 * {@code complete} and {@code missingRates} so callers can tell when an amount
 * was excluded for lack of an exchange rate.
 */
@Service
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final AssetRepository assetRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final UserRepository userRepository;
    private final WebhookRepository webhookRepository;
    private final SoftwareLicenseRepository softwareLicenseRepository;
    private final MoneyAggregator moneyAggregator;

    public DashboardServiceImpl(AssetRepository assetRepository,
                                PurchaseOrderRepository purchaseOrderRepository,
                                MaintenanceRecordRepository maintenanceRecordRepository,
                                UserRepository userRepository,
                                WebhookRepository webhookRepository,
                                SoftwareLicenseRepository softwareLicenseRepository,
                                MoneyAggregator moneyAggregator) {
        this.assetRepository = assetRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.userRepository = userRepository;
        this.webhookRepository = webhookRepository;
        this.softwareLicenseRepository = softwareLicenseRepository;
        this.moneyAggregator = moneyAggregator;
    }

    @Override
    public Map<String, Object> getSummary(Organisation org) {
        List<Asset> assets = assetRepository.findAllByOrganisationAndDeletedAtIsNull(org);
        Set<PurchaseOrder> pos = purchaseOrderRepository.findByOrganisationAndDeletedAtIsNull(org);
        Set<MaintenanceRecord> maint = maintenanceRecordRepository.findByOrganisationAndDeletedAtIsNull(org);
        long userCount = userRepository.countByOrganisationAndDeletedAtIsNull(org);
        List<Webhook> webhooks = webhookRepository.findByOrganisationAndDeletedAtIsNull(org);
        List<SoftwareLicense> licenses = softwareLicenseRepository.findByOrganisationAndDeletedAtIsNull(org);

        LocalDate today = LocalDate.now();
        LocalDate upcoming = today.plusDays(30);

        // Asset counts
        long activeAssets = assets.stream()
                .filter(a -> a.getStatus() == AssetStatus.IN_USE
                          || a.getStatus() == AssetStatus.IN_STOCK
                          || a.getStatus() == AssetStatus.RESERVED)
                .count();
        long inMaintenanceAssets = assets.stream()
                .filter(a -> a.getStatus() == AssetStatus.MAINTENANCE
                          || a.getStatus() == AssetStatus.UNDER_REPAIR)
                .count();
        long disposedAssets = assets.stream()
                .filter(a -> a.getStatus() == AssetStatus.DISPOSED
                          || a.getStatus() == AssetStatus.RETIRED)
                .count();

        // Register value: cost and book value of every asset still on the books
        // (disposed assets excluded), converted to the base currency.
        CurrencyConversion fx = moneyAggregator.begin(org);
        PortfolioValuation valuation = PortfolioValuation.of(fx, assets, today);

        // Purchase order counts
        long pendingApprovals = pos.stream()
                .filter(p -> p.getStatus() == POStatus.SUBMITTED)
                .count();
        long openPurchaseOrders = pos.stream()
                .filter(p -> p.getStatus() == POStatus.SUBMITTED || p.getStatus() == POStatus.DRAFT)
                .count();
        long approvedPOs = pos.stream()
                .filter(p -> p.getStatus() == POStatus.APPROVED || p.getStatus() == POStatus.DELIVERED)
                .count();

        // Maintenance counts — overdue vs upcoming
        long overdueMaintenanceCount = maint.stream()
                .filter(r -> r.getStatus() != MaintenanceStatus.COMPLETED
                          && r.getStatus() != MaintenanceStatus.CANCELLED)
                .filter(r -> r.getNextDueDate() != null && r.getNextDueDate().isBefore(today))
                .count();
        long upcomingMaintenanceCount = maint.stream()
                .filter(r -> r.getStatus() != MaintenanceStatus.COMPLETED
                          && r.getStatus() != MaintenanceStatus.CANCELLED)
                .filter(r -> r.getNextDueDate() != null
                          && !r.getNextDueDate().isBefore(today)
                          && !r.getNextDueDate().isAfter(upcoming))
                .count();

        // License counts
        long expiredLicenses = licenses.stream()
                .filter(l -> l.getStatus() == LicenseStatus.EXPIRED)
                .count();

        // Webhook counts
        long totalWebhooks = webhooks.size();
        long activeWebhooks = webhooks.stream().filter(Webhook::isActive).count();

        Map<String, Object> summary = new LinkedHashMap<>();

        // Asset metrics
        summary.put("totalAssets", assets.size());
        summary.put("activeAssets", activeAssets);
        summary.put("inMaintenanceAssets", inMaintenanceAssets);
        summary.put("disposedAssets", disposedAssets);
        summary.put("totalAssetValue", valuation.cost().amount());
        summary.put("netBookValue", valuation.netBookValue().amount());
        fx.putMetadata(summary);

        // Organisation / user metrics
        summary.put("totalOrganisations", 1);
        summary.put("totalUsers", userCount);

        // Purchase order metrics
        summary.put("pendingApprovals", pendingApprovals);
        summary.put("openPurchaseOrders", openPurchaseOrders);
        summary.put("pendingPOs", pendingApprovals);          // kept for backward compatibility
        summary.put("approvedPOs", approvedPOs);

        // Maintenance metrics
        summary.put("overdueMaintenanceCount", overdueMaintenanceCount);
        summary.put("upcomingMaintenanceCount", upcomingMaintenanceCount);
        summary.put("scheduledMaintenance", upcomingMaintenanceCount);  // kept for backward compatibility
        summary.put("maintenanceAlerts", overdueMaintenanceCount);      // kept for backward compatibility

        // License metrics
        summary.put("expiredLicenses", expiredLicenses);

        // Webhook metrics
        summary.put("totalWebhooks", totalWebhooks);
        summary.put("activeWebhooks", activeWebhooks);

        summary.put("generatedAt", Instant.now().toString());
        return summary;
    }

    @Override
    public Map<String, Object> getAssetsByStatus(Organisation org) {
        List<Asset> assets = assetRepository.findAllByOrganisationAndDeletedAtIsNull(org);
        CurrencyConversion fx = moneyAggregator.begin(org);
        Map<AssetStatus, List<Asset>> grouped = assets.stream()
                .filter(a -> a.getStatus() != null)
                .collect(Collectors.groupingBy(Asset::getStatus));

        List<Map<String, Object>> data = grouped.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", e.getKey().name());
            m.put("name", e.getKey().name());
            m.put("count", e.getValue().size());
            m.put("value", sumValue(fx, e.getValue()).amount());
            m.put("percentage", assets.isEmpty() ? 0 : Math.round((double) e.getValue().size() / assets.size() * 10000.0) / 100.0);
            return m;
        }).collect(Collectors.toList());

        // Sort by count descending
        data.sort((a, b) -> Integer.compare(
                (int) b.get("count"),
                (int) a.get("count")
        ));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("total", assets.size());
        response.put("totalValue", sumValue(fx, assets).amount());
        fx.putMetadata(response);
        return response;
    }

    @Override
    public Map<String, Object> getAssetsByDepartment(Organisation org) {
        List<Asset> assets = assetRepository.findAllByOrganisationAndDeletedAtIsNull(org);
        CurrencyConversion fx = moneyAggregator.begin(org);
        Map<String, List<Asset>> grouped = assets.stream()
                .collect(Collectors.groupingBy(a ->
                        a.getDepartment() != null ? a.getDepartment().getName() : "Unassigned"));

        List<Map<String, Object>> data = grouped.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("departmentName", e.getKey());
            m.put("count", e.getValue().size());
            m.put("value", sumValue(fx, e.getValue()).amount());
            m.put("percentage", assets.isEmpty() ? 0 : Math.round((double) e.getValue().size() / assets.size() * 10000.0) / 100.0);
            return m;
        }).collect(Collectors.toList());

        // Sort by count descending
        data.sort((a, b) -> Integer.compare(
                (int) b.get("count"),
                (int) a.get("count")
        ));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("total", assets.size());
        response.put("totalValue", sumValue(fx, assets).amount());
        fx.putMetadata(response);
        return response;
    }

    @Override
    public Map<String, Object> getMaintenanceAlerts(Organisation org) {
        Set<MaintenanceRecord> records = maintenanceRecordRepository.findByOrganisationAndDeletedAtIsNull(org);
        LocalDate today = LocalDate.now();
        LocalDate warningThreshold = today.plusDays(7);

        List<Map<String, Object>> alerts = records.stream()
                .filter(r -> r.getStatus() != MaintenanceStatus.COMPLETED
                          && r.getStatus() != MaintenanceStatus.CANCELLED)
                .filter(r -> r.getNextDueDate() != null && !r.getNextDueDate().isAfter(today.plusDays(30)))
                .sorted(Comparator.comparing(MaintenanceRecord::getNextDueDate))
                .map(r -> {
                    boolean overdue = r.getNextDueDate().isBefore(today);
                    boolean warningSoon = !overdue && !r.getNextDueDate().isAfter(warningThreshold);

                    String severity;
                    if (overdue) {
                        severity = "critical";
                    } else if (warningSoon) {
                        severity = "warning";
                    } else {
                        severity = "scheduled";
                    }

                    Map<String, Object> alert = new LinkedHashMap<>();
                    alert.put("assetName", r.getAsset() != null ? r.getAsset().getName() : "Unknown Asset");
                    alert.put("severity", severity);
                    alert.put("nextDueDate", r.getNextDueDate().toString());
                    if (overdue) {
                        long daysOverdue = r.getNextDueDate().until(today, ChronoUnit.DAYS);
                        alert.put("daysOverdue", daysOverdue);
                    }
                    return alert;
                })
                .collect(Collectors.toList());

        long criticalCount = alerts.stream().filter(a -> "critical".equals(a.get("severity"))).count();
        long warningCount  = alerts.stream().filter(a -> "warning".equals(a.get("severity"))).count();
        long scheduledCount = alerts.stream().filter(a -> "scheduled".equals(a.get("severity"))).count();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("critical", criticalCount);
        response.put("warning", warningCount);
        response.put("scheduled", scheduledCount);
        response.put("alertCount", alerts.size());
        response.put("alerts", alerts);
        return response;
    }

    @Override
    public Map<String, Object> getDepreciationSummary(Organisation org) {
        List<Asset> assets = assetRepository.findAllByOrganisationAndDeletedAtIsNull(org);
        CurrencyConversion fx = moneyAggregator.begin(org);
        // Disposed assets are off the books; each remaining asset is valued in its
        // own currency by the depreciation engine and only then converted.
        PortfolioValuation valuation = PortfolioValuation.of(fx, assets, LocalDate.now());

        Map<String, Object> response = new LinkedHashMap<>();
        valuation.putTotals(response);
        fx.putMetadata(response);
        return response;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Sum of purchase costs in the base currency; assets without a rate are excluded. */
    private MoneyAccumulator sumValue(CurrencyConversion fx, List<Asset> assets) {
        return fx.sum(assets, Asset::getPurchaseCost, Asset::getCurrency);
    }
}
