package com.example.demo.services.impl;

import com.example.demo.enums.*;
import com.example.demo.models.*;
import com.example.demo.repositories.*;
import com.example.demo.services.DashboardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final AssetRepository assetRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final UserRepository userRepository;
    private final WebhookRepository webhookRepository;
    private final SoftwareLicenseRepository softwareLicenseRepository;

    public DashboardServiceImpl(AssetRepository assetRepository,
                                PurchaseOrderRepository purchaseOrderRepository,
                                MaintenanceRecordRepository maintenanceRecordRepository,
                                UserRepository userRepository,
                                WebhookRepository webhookRepository,
                                SoftwareLicenseRepository softwareLicenseRepository) {
        this.assetRepository = assetRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.userRepository = userRepository;
        this.webhookRepository = webhookRepository;
        this.softwareLicenseRepository = softwareLicenseRepository;
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

        // Total asset value (sum of purchase costs)
        BigDecimal totalAssetValue = sumValue(assets);

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
        summary.put("totalAssetValue", totalAssetValue.setScale(2, RoundingMode.HALF_UP));

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
        Map<AssetStatus, List<Asset>> grouped = assets.stream()
                .filter(a -> a.getStatus() != null)
                .collect(Collectors.groupingBy(Asset::getStatus));

        List<Map<String, Object>> data = grouped.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", e.getKey().name());
            m.put("name", e.getKey().name());
            m.put("count", e.getValue().size());
            m.put("value", sumValue(e.getValue()).setScale(2, RoundingMode.HALF_UP));
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
        response.put("totalValue", sumValue(assets).setScale(2, RoundingMode.HALF_UP));
        return response;
    }

    @Override
    public Map<String, Object> getAssetsByDepartment(Organisation org) {
        List<Asset> assets = assetRepository.findAllByOrganisationAndDeletedAtIsNull(org);
        Map<String, List<Asset>> grouped = assets.stream()
                .collect(Collectors.groupingBy(a ->
                        a.getDepartment() != null ? a.getDepartment().getName() : "Unassigned"));

        List<Map<String, Object>> data = grouped.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("departmentName", e.getKey());
            m.put("count", e.getValue().size());
            m.put("value", sumValue(e.getValue()).setScale(2, RoundingMode.HALF_UP));
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
        response.put("totalValue", sumValue(assets).setScale(2, RoundingMode.HALF_UP));
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
        BigDecimal totalAssetValue = sumValue(assets);
        BigDecimal netBookValue = assets.stream()
                .map(this::calculateDynamicNBV)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDepreciation = totalAssetValue.subtract(netBookValue);
        if (totalDepreciation.signum() < 0) totalDepreciation = BigDecimal.ZERO;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalAssetValue", totalAssetValue.setScale(2, RoundingMode.HALF_UP));
        response.put("totalDepreciation", totalDepreciation.setScale(2, RoundingMode.HALF_UP));
        response.put("netBookValue", netBookValue.setScale(2, RoundingMode.HALF_UP));
        response.put("monthlyDepreciation", calculateMonthlyDepreciation(assets).setScale(2, RoundingMode.HALF_UP));
        response.put("assetsFullyDepreciated", assets.stream().filter(this::isFullyDepreciated).count());

        return response;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private BigDecimal sumValue(List<Asset> assets) {
        return assets.stream()
                .map(a -> a.getPurchaseCost() != null ? a.getPurchaseCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateDynamicNBV(Asset asset) {
        if (asset.getPurchaseCost() == null) return BigDecimal.ZERO;
        if (asset.getUsefulLifeMonths() == null || asset.getUsefulLifeMonths() <= 0 || asset.getPurchaseDate() == null) {
            return asset.getCurrentBookValue() != null ? asset.getCurrentBookValue() : asset.getPurchaseCost();
        }
        long monthsElapsed = asset.getPurchaseDate().until(LocalDate.now(), ChronoUnit.MONTHS);
        if (monthsElapsed <= 0) return asset.getPurchaseCost();
        BigDecimal residual = asset.getResidualValue() != null ? asset.getResidualValue() : BigDecimal.ZERO;
        BigDecimal depreciableAmount = asset.getPurchaseCost().subtract(residual);
        if (depreciableAmount.signum() <= 0) return asset.getPurchaseCost();
        BigDecimal monthlyDep = depreciableAmount.divide(BigDecimal.valueOf(asset.getUsefulLifeMonths()), 2, RoundingMode.HALF_UP);
        BigDecimal accumulatedDep = monthlyDep.multiply(BigDecimal.valueOf(monthsElapsed));
        return asset.getPurchaseCost().subtract(accumulatedDep).max(residual);
    }

    private BigDecimal calculateMonthlyDepreciation(List<Asset> assets) {
        return assets.stream()
                .filter(a -> a.getPurchaseCost() != null && a.getUsefulLifeMonths() != null && a.getUsefulLifeMonths() > 0)
                .map(a -> {
                    BigDecimal dep = a.getPurchaseCost().subtract(
                            a.getResidualValue() != null ? a.getResidualValue() : BigDecimal.ZERO);
                    if (dep.signum() <= 0) return BigDecimal.ZERO;
                    return dep.divide(BigDecimal.valueOf(a.getUsefulLifeMonths()), 2, RoundingMode.HALF_UP);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isFullyDepreciated(Asset a) {
        BigDecimal nbv = calculateDynamicNBV(a);
        BigDecimal residual = a.getResidualValue() != null ? a.getResidualValue() : BigDecimal.ZERO;
        return nbv.compareTo(residual) <= 0;
    }
}
