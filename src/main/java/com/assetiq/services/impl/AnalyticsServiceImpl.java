package com.assetiq.services.impl;

import com.assetiq.enums.AssetCondition;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.MaintenanceStatus;
import com.assetiq.enums.MaintenanceType;
import com.assetiq.enums.POStatus;
import com.assetiq.models.Asset;
import com.assetiq.models.MaintenanceRecord;
import com.assetiq.models.Organisation;
import com.assetiq.models.PurchaseOrder;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.BudgetRepository;
import com.assetiq.repositories.DisposalRecordRepository;
import com.assetiq.repositories.MaintenanceRecordRepository;
import com.assetiq.repositories.PurchaseOrderRepository;
import com.assetiq.services.AnalyticsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    private final AssetRepository assetRepository;
    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final BudgetRepository budgetRepository;
    private final DisposalRecordRepository disposalRecordRepository;

    public AnalyticsServiceImpl(AssetRepository assetRepository,
                                MaintenanceRecordRepository maintenanceRecordRepository,
                                PurchaseOrderRepository purchaseOrderRepository,
                                BudgetRepository budgetRepository,
                                DisposalRecordRepository disposalRecordRepository) {
        this.assetRepository = assetRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.budgetRepository = budgetRepository;
        this.disposalRecordRepository = disposalRecordRepository;
    }

    // ── Asset Analytics ───────────────────────────────────────────────────────

    @Override
    public Map<String, Object> getAssetAnalytics(String period, String groupBy, Organisation org) {
        List<Asset> assets = assetRepository.findAllByOrganisationAndDeletedAtIsNull(org);
        long total = assets.size();
        BigDecimal totalValue = sumPurchaseCost(assets);

        List<Map<String, Object>> data;
        switch (groupBy.toLowerCase()) {
            case "status" -> {
                Map<String, List<Asset>> grouped = assets.stream().collect(
                        Collectors.groupingBy(a -> a.getStatus() != null ? a.getStatus().name() : AssetStatus.IN_USE.name()));
                data = buildGroups(grouped, total, totalValue);
            }
            case "department" -> {
                Map<String, List<Asset>> grouped = assets.stream().collect(
                        Collectors.groupingBy(a -> a.getDepartment() != null ? a.getDepartment().getName() : "Unassigned"));
                data = buildGroups(grouped, total, totalValue);
            }
            case "condition" -> {
                Map<String, List<Asset>> grouped = assets.stream().collect(
                        Collectors.groupingBy(a -> a.getCondition() != null ? a.getCondition().name() : AssetCondition.GOOD.name()));
                data = buildGroups(grouped, total, totalValue);
            }
            default -> throw new IllegalArgumentException(
                    "Invalid groupBy '" + groupBy + "'. Allowed values: status, department, condition");
        }

        data.sort((a, b) -> Long.compare(toLong(b.get("count")), toLong(a.get("count"))));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("period", period);
        response.put("groupBy", groupBy);
        response.put("data", data);
        response.put("total", total);
        response.put("totalValue", totalValue.setScale(2, RoundingMode.HALF_UP));
        response.put("generatedAt", Instant.now().toString());
        return response;
    }

    // ── Financial Analytics ───────────────────────────────────────────────────

    @Override
    public Map<String, Object> getFinancialAnalytics(String period, Organisation org) {
        List<Asset> assets = assetRepository.findAllByOrganisationAndDeletedAtIsNull(org);
        Set<MaintenanceRecord> records = maintenanceRecordRepository.findByOrganisationAndDeletedAtIsNull(org);

        BigDecimal totalAssetValue = sumPurchaseCost(assets);

        BigDecimal netBookValue = assets.stream()
                .map(this::calculateDynamicNBV)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDepreciation = totalAssetValue.subtract(netBookValue);
        if (totalDepreciation.signum() < 0) totalDepreciation = BigDecimal.ZERO;

        BigDecimal totalMaintenanceCost = records.stream()
                .map(m -> m.getCost() != null ? m.getCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Sum of monthly depreciation charges across all actively-depreciating assets
        BigDecimal monthlyDepreciation = assets.stream()
                .filter(a -> a.getPurchaseCost() != null
                        && a.getUsefulLifeMonths() != null
                        && a.getUsefulLifeMonths() > 0)
                .map(a -> {
                    BigDecimal depreciable = a.getPurchaseCost()
                            .subtract(a.getResidualValue() != null ? a.getResidualValue() : BigDecimal.ZERO);
                    if (depreciable.signum() <= 0) return BigDecimal.ZERO;
                    return depreciable.divide(BigDecimal.valueOf(a.getUsefulLifeMonths()), 2, RoundingMode.HALF_UP);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long assetsFullyDepreciated = assets.stream()
                .filter(a -> a.getCurrentBookValue() != null && a.getResidualValue() != null
                        && a.getCurrentBookValue().compareTo(a.getResidualValue()) <= 0)
                .count();

        double averageAgeMonths = assets.stream()
                .filter(a -> a.getPurchaseDate() != null)
                .mapToLong(a -> a.getPurchaseDate().until(LocalDate.now(), ChronoUnit.MONTHS))
                .average()
                .orElse(0.0);

        // Breakdown by category
        Map<String, List<Asset>> byCategory = assets.stream().collect(
                Collectors.groupingBy(a -> a.getCategory() != null ? a.getCategory().getName() : "Uncategorized"));

        Map<String, Object> categoryBreakdown = new LinkedHashMap<>();
        byCategory.forEach((catName, catAssets) -> {
            BigDecimal catValue = sumPurchaseCost(catAssets);
            BigDecimal catNBV = catAssets.stream()
                    .map(a -> a.getCurrentBookValue() != null ? a.getCurrentBookValue() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal catMonthlyDep = catAssets.stream()
                    .filter(a -> a.getPurchaseCost() != null && a.getUsefulLifeMonths() != null && a.getUsefulLifeMonths() > 0)
                    .map(a -> {
                        BigDecimal dep = a.getPurchaseCost()
                                .subtract(a.getResidualValue() != null ? a.getResidualValue() : BigDecimal.ZERO);
                        if (dep.signum() <= 0) return BigDecimal.ZERO;
                        return dep.divide(BigDecimal.valueOf(a.getUsefulLifeMonths()), 2, RoundingMode.HALF_UP);
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", catName);
            m.put("count", catAssets.size());
            m.put("value", catValue.setScale(2, RoundingMode.HALF_UP));
            m.put("netBookValue", catNBV.setScale(2, RoundingMode.HALF_UP));
            m.put("monthlyDepreciation", catMonthlyDep.setScale(2, RoundingMode.HALF_UP));
            categoryBreakdown.put(catName, m);
        });

        // Period-specific Acquisition/Disposal
        LocalDate start = getPeriodStart(period);
        LocalDate end = LocalDate.now();

        BigDecimal totalAcquisition = assets.stream()
                .filter(a -> a.getPurchaseDate() != null && !a.getPurchaseDate().isBefore(start) && !a.getPurchaseDate().isAfter(end))
                .map(a -> a.getPurchaseCost() != null ? a.getPurchaseCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDisposal = disposalRecordRepository.findByOrganisationAndDisposalDateBetweenAndDeletedAtIsNull(org, start, end)
                .stream()
                .map(d -> d.getSaleValue() != null ? d.getSaleValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Budget Consolidation
        List<com.assetiq.models.Budget> budgets = budgetRepository.findByOrganisationAndDeletedAtIsNullOrderByPeriodStartDesc(org);
        BigDecimal totalBudget = budgets.stream()
                .map(b -> b.getTotalAmount() != null ? b.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actualSpend = budgets.stream()
                .map(b -> b.getSpentAmount() != null ? b.getSpentAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double budgetUtilization = totalBudget.signum() > 0
                ? actualSpend.divide(totalBudget, 4, RoundingMode.HALF_UP).doubleValue() * 100
                : 0.0;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("period", period);
        response.put("totalAssets", assets.size());
        response.put("totalAssetValue", totalAssetValue.setScale(2, RoundingMode.HALF_UP));
        response.put("totalDepreciation", totalDepreciation.setScale(2, RoundingMode.HALF_UP));
        response.put("netBookValue", netBookValue.setScale(2, RoundingMode.HALF_UP));
        response.put("totalMaintenance", totalMaintenanceCost.setScale(2, RoundingMode.HALF_UP));
        response.put("monthlyDepreciation", monthlyDepreciation.setScale(2, RoundingMode.HALF_UP));
        response.put("totalAcquisition", totalAcquisition.setScale(2, RoundingMode.HALF_UP));
        response.put("totalDisposal", totalDisposal.setScale(2, RoundingMode.HALF_UP));
        response.put("totalBudget", totalBudget.setScale(2, RoundingMode.HALF_UP));
        response.put("totalActualSpend", actualSpend.setScale(2, RoundingMode.HALF_UP));
        response.put("budgetUtilization", Math.round(budgetUtilization * 100.0) / 100.0);
        response.put("assetsFullyDepreciated", assetsFullyDepreciated);
        response.put("averageAssetAgeMonths", Math.round(averageAgeMonths * 10.0) / 10.0);
        response.put("breakdown", Map.of("byCategory", categoryBreakdown));
        response.put("generatedAt", Instant.now().toString());
        return response;
    }

    // ── Purchase Order Analytics ──────────────────────────────────────────────

    @Override
    public Map<String, Object> getPurchaseOrderAnalytics(String period, Organisation org) {
        Set<PurchaseOrder> pos = purchaseOrderRepository.findByOrganisationAndDeletedAtIsNull(org);
        long total = pos.size();

        long draft = pos.stream().filter(p -> p.getStatus() == POStatus.DRAFT).count();
        long submitted = pos.stream().filter(p -> p.getStatus() == POStatus.SUBMITTED).count();
        long approved = pos.stream().filter(p -> p.getStatus() == POStatus.APPROVED || p.getStatus() == POStatus.DELIVERED).count();
        long rejected = pos.stream().filter(p -> p.getStatus() == POStatus.REJECTED || p.getStatus() == POStatus.CANCELLED).count();

        BigDecimal totalValue = pos.stream()
                .map(p -> p.getTotalAmount() != null ? p.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageValue = total > 0
                ? totalValue.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal largestPO = pos.stream()
                .map(p -> p.getTotalAmount() != null ? p.getTotalAmount() : BigDecimal.ZERO)
                .max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);

        BigDecimal smallestPO = pos.stream()
                .filter(p -> p.getTotalAmount() != null && p.getTotalAmount().signum() > 0)
                .map(PurchaseOrder::getTotalAmount)
                .min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);

        // Top 5 suppliers by PO count
        Map<String, List<PurchaseOrder>> bySupplier = pos.stream()
                .filter(p -> p.getSupplier() != null)
                .collect(Collectors.groupingBy(p -> p.getSupplier().getName()));

        List<Map<String, Object>> topSuppliers = bySupplier.entrySet().stream()
                .map(e -> {
                    BigDecimal supplierTotal = e.getValue().stream()
                            .map(p -> p.getTotalAmount() != null ? p.getTotalAmount() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("supplier", e.getKey());
                    m.put("poCount", e.getValue().size());
                    m.put("totalValue", supplierTotal.setScale(2, RoundingMode.HALF_UP));
                    return m;
                })
                .sorted((a, b) -> Integer.compare((int) b.get("poCount"), (int) a.get("poCount")))
                .limit(5)
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("period", period);
        response.put("totalPOs", total);
        response.put("draftPOs", draft);
        response.put("submittedPOs", submitted);
        response.put("approvedPOs", approved);
        response.put("rejectedPOs", rejected);
        response.put("totalPOValue", totalValue.setScale(2, RoundingMode.HALF_UP));
        response.put("averagePOValue", averageValue);
        response.put("largestPO", largestPO.setScale(2, RoundingMode.HALF_UP));
        response.put("smallestPO", smallestPO.setScale(2, RoundingMode.HALF_UP));
        response.put("topSuppliers", topSuppliers);
        response.put("generatedAt", Instant.now().toString());
        return response;
    }

    // ── Maintenance Analytics ─────────────────────────────────────────────────

    @Override
    public Map<String, Object> getMaintenanceAnalytics(Organisation org) {
        Set<MaintenanceRecord> records = maintenanceRecordRepository.findByOrganisationAndDeletedAtIsNull(org);
        long total = records.size();

        long assetsMaintained = records.stream()
                .filter(r -> r.getAsset() != null)
                .map(r -> r.getAsset().getId())
                .distinct().count();

        BigDecimal totalCost = records.stream()
                .map(r -> r.getCost() != null ? r.getCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgCost = total > 0
                ? totalCost.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Assets with overdue or upcoming maintenance (nextDueDate <= today, not yet done)
        LocalDate today = LocalDate.now();
        long assetsNeedingMaintenance = records.stream()
                .filter(r -> r.getNextDueDate() != null
                        && !r.getNextDueDate().isAfter(today)
                        && r.getStatus() != MaintenanceStatus.COMPLETED
                        && r.getStatus() != MaintenanceStatus.CANCELLED)
                .filter(r -> r.getAsset() != null)
                .map(r -> r.getAsset().getId())
                .distinct().count();

        // Count by maintenance type
        Map<String, Long> countByType = records.stream()
                .filter(r -> r.getMaintenanceType() != null)
                .collect(Collectors.groupingBy(r -> r.getMaintenanceType().name(), Collectors.counting()));
        // Ensure all types appear even if zero
        for (MaintenanceType t : MaintenanceType.values()) {
            countByType.putIfAbsent(t.name(), 0L);
        }

        // Cost by maintenance type
        Map<String, BigDecimal> costByType = records.stream()
                .filter(r -> r.getMaintenanceType() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getMaintenanceType().name(),
                        Collectors.reducing(BigDecimal.ZERO,
                                r -> r.getCost() != null ? r.getCost() : BigDecimal.ZERO,
                                BigDecimal::add)));
        costByType.replaceAll((k, v) -> v.setScale(2, RoundingMode.HALF_UP));
        for (MaintenanceType t : MaintenanceType.values()) {
            costByType.putIfAbsent(t.name(), BigDecimal.ZERO);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalMaintenanceRecords", total);
        response.put("assetsMaintained", assetsMaintained);
        response.put("totalMaintenanceCost", totalCost.setScale(2, RoundingMode.HALF_UP));
        response.put("averageMaintenanceCost", avgCost);
        response.put("assetsNeedingMaintenance", assetsNeedingMaintenance);
        response.put("countByType", countByType);
        response.put("costByType", costByType);
        response.put("generatedAt", Instant.now().toString());
        return response;
    }

    // ── Depreciation Trends ───────────────────────────────────────────────────

    @Override
    public Map<String, Object> getDepreciationTrends(int months, Organisation org) {
        months = Math.max(1, Math.min(60, months));
        List<Asset> assets = assetRepository.findAllByOrganisationAndDeletedAtIsNull(org);

        YearMonth current = YearMonth.now();
        List<Map<String, Object>> trends = new ArrayList<>();
        BigDecimal totalDepreciationOverPeriod = BigDecimal.ZERO;

        for (int i = months - 1; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            LocalDate monthEnd = ym.atEndOfMonth();

            BigDecimal totalValue = BigDecimal.ZERO;
            BigDecimal monthlyDepreciationCharge = BigDecimal.ZERO;
            int assetsRetiredOrDisposed = 0;

            for (Asset asset : assets) {
                if (asset.getPurchaseCost() == null) continue;
                // Skip assets not yet purchased at this point in time
                if (asset.getPurchaseDate() != null && asset.getPurchaseDate().isAfter(monthEnd)) continue;

                if (asset.getUsefulLifeMonths() != null && asset.getUsefulLifeMonths() > 0
                        && asset.getPurchaseDate() != null) {
                    long monthsElapsed = asset.getPurchaseDate().until(monthEnd, ChronoUnit.MONTHS);
                    BigDecimal residual = asset.getResidualValue() != null ? asset.getResidualValue() : BigDecimal.ZERO;
                    BigDecimal depreciable = asset.getPurchaseCost().subtract(residual);
                    if (depreciable.signum() <= 0) {
                        totalValue = totalValue.add(asset.getPurchaseCost());
                        continue;
                    }
                    BigDecimal monthlyDep = depreciable.divide(
                            BigDecimal.valueOf(asset.getUsefulLifeMonths()), 2, RoundingMode.HALF_UP);
                    BigDecimal accDep = monthlyDep.multiply(BigDecimal.valueOf(monthsElapsed));
                    if (accDep.compareTo(depreciable) > 0) accDep = depreciable;
                    BigDecimal bookValue = asset.getPurchaseCost().subtract(accDep).max(residual);
                    totalValue = totalValue.add(bookValue);

                    // Depreciation charge for this month (zero if already fully depreciated)
                    BigDecimal remaining = depreciable.subtract(accDep.subtract(monthlyDep).max(BigDecimal.ZERO));
                    BigDecimal charge = monthlyDep.min(remaining).max(BigDecimal.ZERO);
                    monthlyDepreciationCharge = monthlyDepreciationCharge.add(charge);
                } else {
                    // No depreciation data: carry purchase cost as book value
                    totalValue = totalValue.add(asset.getPurchaseCost());
                }

                if (asset.getStatus() == AssetStatus.RETIRED || asset.getStatus() == AssetStatus.DISPOSED) {
                    assetsRetiredOrDisposed++;
                }
            }

            BigDecimal charge = monthlyDepreciationCharge.setScale(2, RoundingMode.HALF_UP);
            totalDepreciationOverPeriod = totalDepreciationOverPeriod.add(charge);

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("month", ym.toString());
            point.put("totalValue", totalValue.setScale(2, RoundingMode.HALF_UP));
            point.put("monthlyDepreciation", charge);
            point.put("assetsRetiredOrDisposed", assetsRetiredOrDisposed);
            trends.add(point);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("months", months);
        response.put("trends", trends);
        response.put("totalDepreciationOverPeriod", totalDepreciationOverPeriod.setScale(2, RoundingMode.HALF_UP));
        response.put("generatedAt", Instant.now().toString());
        return response;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BigDecimal sumPurchaseCost(List<Asset> assets) {
        return assets.stream()
                .map(a -> a.getPurchaseCost() != null ? a.getPurchaseCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<Map<String, Object>> buildGroups(Map<String, List<Asset>> grouped, long total, BigDecimal totalValue) {
        return grouped.entrySet().stream().map(e -> {
            long count = e.getValue().size();
            BigDecimal value = sumPurchaseCost(e.getValue());
            double pct = total > 0 ? Math.round((count * 1000.0 / total)) / 10.0 : 0.0;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", e.getKey());
            m.put("count", count);
            m.put("value", value.setScale(2, RoundingMode.HALF_UP));
            m.put("percentage", pct);
            return m;
        }).collect(Collectors.toList());
    }

    private long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private LocalDate getPeriodStart(String period) {
        LocalDate today = LocalDate.now();
        return switch (period.toLowerCase()) {
            case "week" -> today.minusWeeks(1);
            case "quarter" -> today.minusMonths(3);
            case "year" -> today.minusYears(1);
            default -> today.withDayOfMonth(1); // month
        };
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
}
