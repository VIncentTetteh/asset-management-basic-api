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
import com.assetiq.models.Budget;
import com.assetiq.models.DisposalRecord;
import com.assetiq.services.AnalyticsService;
import com.assetiq.services.finance.DepreciationCalculator;
import com.assetiq.services.finance.PortfolioValuation;
import com.assetiq.services.money.CurrencyConversion;
import com.assetiq.services.money.MoneyAccumulator;
import com.assetiq.services.money.MoneyAggregator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analytics aggregates. All money figures are converted into the tenant base
 * currency through {@link MoneyAggregator}; every response carries
 * {@code currency}, {@code complete} and {@code missingRates}.
 *
 * <p>Maintenance costs and disposal sale values carry their own currency (V42);
 * legacy rows without one are in the related asset's currency (base currency
 * when the record has no asset).
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    private final AssetRepository assetRepository;
    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final BudgetRepository budgetRepository;
    private final DisposalRecordRepository disposalRecordRepository;
    private final MoneyAggregator moneyAggregator;

    public AnalyticsServiceImpl(AssetRepository assetRepository,
                                MaintenanceRecordRepository maintenanceRecordRepository,
                                PurchaseOrderRepository purchaseOrderRepository,
                                BudgetRepository budgetRepository,
                                DisposalRecordRepository disposalRecordRepository,
                                MoneyAggregator moneyAggregator) {
        this.assetRepository = assetRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.budgetRepository = budgetRepository;
        this.disposalRecordRepository = disposalRecordRepository;
        this.moneyAggregator = moneyAggregator;
    }

    // ── Asset Analytics ───────────────────────────────────────────────────────

    @Override
    public Map<String, Object> getAssetAnalytics(String period, String groupBy, Organisation org) {
        LocalDate start = getPeriodStart(period);
        LocalDate end = LocalDate.now();
        List<Asset> assets = assetRepository.findAllByOrganisationAndDeletedAtIsNull(org).stream()
                .filter(asset -> isWithinPeriod(assetDate(asset), start, end))
                .toList();
        long total = assets.size();
        CurrencyConversion fx = moneyAggregator.begin(org);
        BigDecimal totalValue = sumPurchaseCost(fx, assets).amount();

        List<Map<String, Object>> data;
        switch (groupBy.toLowerCase()) {
            case "status" -> {
                Map<String, List<Asset>> grouped = assets.stream().collect(
                        Collectors.groupingBy(a -> a.getStatus() != null ? a.getStatus().name() : AssetStatus.IN_USE.name()));
                data = buildGroups(fx, grouped, total);
            }
            case "department" -> {
                Map<String, List<Asset>> grouped = assets.stream().collect(
                        Collectors.groupingBy(a -> a.getDepartment() != null ? a.getDepartment().getName() : "Unassigned"));
                data = buildGroups(fx, grouped, total);
            }
            case "condition" -> {
                Map<String, List<Asset>> grouped = assets.stream().collect(
                        Collectors.groupingBy(a -> a.getCondition() != null ? a.getCondition().name() : AssetCondition.GOOD.name()));
                data = buildGroups(fx, grouped, total);
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
        response.put("totalValue", totalValue);
        fx.putMetadata(response);
        response.put("generatedAt", Instant.now().toString());
        return response;
    }

    // ── Financial Analytics ───────────────────────────────────────────────────

    /**
     * Two kinds of figure, kept apart on purpose:
     * <ul>
     *   <li><b>Portfolio</b> (totalAssets, totalAssetValue, netBookValue, totalDepreciation,
     *       monthlyDepreciation, fully-depreciated / missing-setup counts, average age,
     *       category breakdown): every asset still on the books as of today, whatever its
     *       acquisition date. Disposed assets are excluded.</li>
     *   <li><b>Period activity</b> (acquisitionsInPeriod, totalAcquisition, totalMaintenance,
     *       totalDisposal): only records dated inside {@code period}.</li>
     * </ul>
     */
    @Override
    public Map<String, Object> getFinancialAnalytics(String period, Organisation org) {
        LocalDate start = getPeriodStart(period);
        LocalDate end = LocalDate.now();
        List<Asset> allAssets = assetRepository.findAllByOrganisationAndDeletedAtIsNull(org);
        List<Asset> onBooks = allAssets.stream().filter(PortfolioValuation::isOnBooks).toList();
        List<Asset> acquired = allAssets.stream()
                .filter(asset -> isWithinPeriod(assetDate(asset), start, end))
                .toList();
        Set<MaintenanceRecord> records = maintenanceRecordRepository.findByOrganisationAndDeletedAtIsNull(org).stream()
                .filter(record -> isWithinPeriod(maintenanceDate(record), start, end))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        CurrencyConversion fx = moneyAggregator.begin(org);

        // Portfolio: per-asset figures come from the depreciation engine in the
        // asset's currency, then are converted.
        PortfolioValuation portfolio = PortfolioValuation.of(fx, onBooks, end);

        // Maintenance cost is in the record's currency (legacy rows: the asset's).
        MoneyAccumulator totalMaintenanceCost = fx.sum(records, MaintenanceRecord::getCost, this::maintenanceCurrency);

        double averageAgeMonths = onBooks.stream()
                .filter(a -> a.getPurchaseDate() != null)
                .mapToLong(a -> a.getPurchaseDate().until(end, ChronoUnit.MONTHS))
                .average()
                .orElse(0.0);

        // Breakdown by category: the same valuation per category, so the categories
        // add up to the portfolio totals.
        Map<String, List<Asset>> byCategory = onBooks.stream().collect(
                Collectors.groupingBy(a -> a.getCategory() != null ? a.getCategory().getName() : "Uncategorized"));

        Map<String, Object> categoryBreakdown = new LinkedHashMap<>();
        byCategory.forEach((catName, catAssets) -> {
            PortfolioValuation cat = PortfolioValuation.of(fx, catAssets, end);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", catName);
            m.put("count", catAssets.size());
            m.put("value", cat.cost().amount());
            m.put("netBookValue", cat.netBookValue().amount());
            m.put("accumulatedDepreciation", cat.accumulatedDepreciation().amount());
            m.put("monthlyDepreciation", cat.monthlyDepreciation().amount());
            categoryBreakdown.put(catName, m);
        });

        MoneyAccumulator totalAcquisition = sumPurchaseCost(fx, acquired);

        // Disposal sale value is in the record's currency (legacy rows: the asset's).
        MoneyAccumulator totalDisposal = fx.sum(
                disposalRecordRepository.findByOrganisationAndDisposalDateBetweenAndDeletedAtIsNull(org, start, end),
                DisposalRecord::getSaleValue,
                DisposalRecord::effectiveCurrency);

        // Budget Consolidation
        List<Budget> budgets = budgetRepository.findByOrganisationAndDeletedAtIsNullOrderByPeriodStartDesc(org);
        MoneyAccumulator totalBudget = fx.sum(budgets, Budget::getTotalAmount, Budget::getCurrency);
        MoneyAccumulator actualSpend = fx.sum(budgets, Budget::getSpentAmount, Budget::getCurrency);

        // Utilisation from converted values, never from a mix of currencies.
        double budgetUtilization = totalBudget.rawSum().signum() > 0
                ? actualSpend.rawSum().divide(totalBudget.rawSum(), 4, RoundingMode.HALF_UP).doubleValue() * 100
                : 0.0;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("period", period);
        response.put("periodStart", start.toString());
        response.put("totalAssets", portfolio.assetCount());
        portfolio.putTotals(response);
        response.put("totalMaintenance", totalMaintenanceCost.amount());
        response.put("acquisitionsInPeriod", acquired.size());
        response.put("totalAcquisition", totalAcquisition.amount());
        response.put("totalDisposal", totalDisposal.amount());
        response.put("totalBudget", totalBudget.amount());
        response.put("totalActualSpend", actualSpend.amount());
        response.put("budgetUtilization", Math.round(budgetUtilization * 100.0) / 100.0);
        response.put("averageAssetAgeMonths", Math.round(averageAgeMonths * 10.0) / 10.0);
        response.put("breakdown", Map.of("byCategory", categoryBreakdown));
        fx.putMetadata(response);
        response.put("generatedAt", Instant.now().toString());
        return response;
    }

    // ── Purchase Order Analytics ──────────────────────────────────────────────

    @Override
    public Map<String, Object> getPurchaseOrderAnalytics(String period, Organisation org) {
        LocalDate start = getPeriodStart(period);
        LocalDate end = LocalDate.now();
        Set<PurchaseOrder> pos = purchaseOrderRepository.findByOrganisationAndDeletedAtIsNull(org).stream()
                .filter(po -> isWithinPeriod(toLocalDate(po.getCreatedAt()), start, end))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        long total = pos.size();

        long draft = pos.stream().filter(p -> p.getStatus() == POStatus.DRAFT).count();
        long submitted = pos.stream().filter(p -> p.getStatus() == POStatus.SUBMITTED).count();
        long approved = pos.stream().filter(p -> p.getStatus() == POStatus.APPROVED || p.getStatus() == POStatus.DELIVERED).count();
        long rejected = pos.stream().filter(p -> p.getStatus() == POStatus.REJECTED || p.getStatus() == POStatus.CANCELLED).count();

        CurrencyConversion fx = moneyAggregator.begin(org);

        // Convert each PO once; a PO whose currency has no rate is left out of the
        // total, the average and the largest/smallest comparison alike.
        List<BigDecimal> convertedValues = new ArrayList<>();
        for (PurchaseOrder p : pos) {
            BigDecimal amount = p.getTotalAmount() != null ? p.getTotalAmount() : BigDecimal.ZERO;
            fx.toBase(amount, p.getCurrency()).ifPresent(convertedValues::add);
        }
        BigDecimal totalValue = convertedValues.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageValue = convertedValues.isEmpty()
                ? CurrencyConversion.round(BigDecimal.ZERO)
                : totalValue.divide(BigDecimal.valueOf(convertedValues.size()),
                        CurrencyConversion.MONEY_SCALE, CurrencyConversion.MONEY_ROUNDING);

        BigDecimal largestPO = convertedValues.stream()
                .max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);

        BigDecimal smallestPO = convertedValues.stream()
                .filter(v -> v.signum() > 0)
                .min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);

        // Top 5 suppliers by PO count
        Map<String, List<PurchaseOrder>> bySupplier = pos.stream()
                .filter(p -> p.getSupplier() != null)
                .collect(Collectors.groupingBy(p -> p.getSupplier().getName()));

        List<Map<String, Object>> topSuppliers = bySupplier.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("supplier", e.getKey());
                    m.put("poCount", e.getValue().size());
                    m.put("totalValue",
                            fx.sum(e.getValue(), PurchaseOrder::getTotalAmount, PurchaseOrder::getCurrency).amount());
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
        response.put("totalPOValue", CurrencyConversion.round(totalValue));
        response.put("averagePOValue", averageValue);
        response.put("largestPO", CurrencyConversion.round(largestPO));
        response.put("smallestPO", CurrencyConversion.round(smallestPO));
        response.put("topSuppliers", topSuppliers);
        fx.putMetadata(response);
        response.put("generatedAt", Instant.now().toString());
        return response;
    }

    // ── Maintenance Analytics ─────────────────────────────────────────────────

    @Override
    public Map<String, Object> getMaintenanceAnalytics(String period, Organisation org) {
        LocalDate start = getPeriodStart(period);
        LocalDate end = LocalDate.now();
        Set<MaintenanceRecord> records = maintenanceRecordRepository.findByOrganisationAndDeletedAtIsNull(org).stream()
                .filter(record -> isWithinPeriod(maintenanceDate(record), start, end))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        long total = records.size();

        long assetsMaintained = records.stream()
                .filter(r -> r.getAsset() != null)
                .map(r -> r.getAsset().getId())
                .distinct().count();

        CurrencyConversion fx = moneyAggregator.begin(org);
        // Maintenance cost is in the record's currency (legacy rows: the asset's).
        // Records without a cost count as zero in the average, as before; records
        // whose currency has no rate are excluded from both total and average.
        BigDecimal totalCost = BigDecimal.ZERO;
        long costedRecords = 0;
        for (MaintenanceRecord r : records) {
            BigDecimal cost = r.getCost() != null ? r.getCost() : BigDecimal.ZERO;
            Optional<BigDecimal> converted = fx.toBase(cost, maintenanceCurrency(r));
            if (converted.isPresent()) {
                totalCost = totalCost.add(converted.get());
                costedRecords++;
            }
        }

        BigDecimal avgCost = costedRecords > 0
                ? totalCost.divide(BigDecimal.valueOf(costedRecords),
                        CurrencyConversion.MONEY_SCALE, CurrencyConversion.MONEY_ROUNDING)
                : CurrencyConversion.round(BigDecimal.ZERO);

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
        Map<String, MoneyAccumulator> costAccByType = new LinkedHashMap<>();
        records.stream()
                .filter(r -> r.getMaintenanceType() != null)
                .forEach(r -> costAccByType
                        .computeIfAbsent(r.getMaintenanceType().name(), k -> fx.newAccumulator())
                        .add(r.getCost(), maintenanceCurrency(r)));
        Map<String, BigDecimal> costByType = new LinkedHashMap<>();
        costAccByType.forEach((k, acc) -> costByType.put(k, acc.amount()));
        for (MaintenanceType t : MaintenanceType.values()) {
            costByType.putIfAbsent(t.name(), CurrencyConversion.round(BigDecimal.ZERO));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("period", period);
        response.put("totalMaintenanceRecords", total);
        response.put("assetsMaintained", assetsMaintained);
        response.put("totalMaintenanceCost", CurrencyConversion.round(totalCost));
        response.put("averageMaintenanceCost", avgCost);
        response.put("assetsNeedingMaintenance", assetsNeedingMaintenance);
        response.put("countByType", countByType);
        response.put("costByType", costByType);
        fx.putMetadata(response);
        response.put("generatedAt", Instant.now().toString());
        return response;
    }

    // ── Depreciation Trends ───────────────────────────────────────────────────

    @Override
    public Map<String, Object> getDepreciationTrends(int months, Organisation org) {
        months = Math.max(1, Math.min(60, months));
        List<Asset> assets = assetRepository.findAllByOrganisationAndDeletedAtIsNull(org);

        // Book values and charges are computed in each asset's currency, then
        // converted at today's rate so every month is expressed in one currency.
        CurrencyConversion fx = moneyAggregator.begin(org);
        YearMonth current = YearMonth.now();
        List<Map<String, Object>> trends = new ArrayList<>();
        BigDecimal totalDepreciationOverPeriod = BigDecimal.ZERO;

        for (int i = months - 1; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            LocalDate monthEnd = ym.atEndOfMonth();

            MoneyAccumulator totalValue = fx.newAccumulator();
            MoneyAccumulator monthlyDepreciationCharge = fx.newAccumulator();
            int assetsRetiredOrDisposed = 0;

            LocalDate priorMonthEnd = ym.minusMonths(1).atEndOfMonth();
            for (Asset asset : assets) {
                if (asset.getPurchaseCost() == null) continue;
                // Skip assets not yet purchased at this point in time
                if (asset.getPurchaseDate() != null && asset.getPurchaseDate().isAfter(monthEnd)) continue;

                if (asset.getStatus() == AssetStatus.RETIRED || asset.getStatus() == AssetStatus.DISPOSED) {
                    assetsRetiredOrDisposed++;
                }
                // Disposed assets are off the books (no disposal date is tracked on
                // the asset, so they are excluded from every month alike).
                if (!PortfolioValuation.isOnBooks(asset)) continue;

                DepreciationCalculator.Result atMonthEnd = DepreciationCalculator.forAsset(asset, monthEnd);
                totalValue.add(atMonthEnd.netBookValue(), asset.getCurrency());
                // Charge taken during this calendar month (zero once fully depreciated)
                BigDecimal charge = atMonthEnd.accumulatedDepreciation().subtract(
                        DepreciationCalculator.forAsset(asset, priorMonthEnd).accumulatedDepreciation());
                monthlyDepreciationCharge.add(charge.max(BigDecimal.ZERO), asset.getCurrency());
            }

            BigDecimal charge = monthlyDepreciationCharge.amount();
            totalDepreciationOverPeriod = totalDepreciationOverPeriod.add(charge);

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("month", ym.toString());
            point.put("totalValue", totalValue.amount());
            point.put("monthlyDepreciation", charge);
            point.put("assetsRetiredOrDisposed", assetsRetiredOrDisposed);
            trends.add(point);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("months", months);
        response.put("trends", trends);
        response.put("totalDepreciationOverPeriod", CurrencyConversion.round(totalDepreciationOverPeriod));
        fx.putMetadata(response);
        response.put("generatedAt", Instant.now().toString());
        return response;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Purchase costs converted into the base currency; unconvertible assets are excluded. */
    private MoneyAccumulator sumPurchaseCost(CurrencyConversion fx, List<Asset> assets) {
        return fx.sum(assets, Asset::getPurchaseCost, Asset::getCurrency);
    }

    /** A maintenance cost's currency: the record's own, else (legacy rows) the asset's. */
    private String maintenanceCurrency(MaintenanceRecord record) {
        return record.effectiveCurrency();
    }

    private List<Map<String, Object>> buildGroups(CurrencyConversion fx, Map<String, List<Asset>> grouped, long total) {
        return grouped.entrySet().stream().map(e -> {
            long count = e.getValue().size();
            BigDecimal value = sumPurchaseCost(fx, e.getValue()).amount();
            double pct = total > 0 ? Math.round((count * 1000.0 / total)) / 10.0 : 0.0;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", e.getKey());
            m.put("count", count);
            m.put("value", value);
            m.put("percentage", pct);
            return m;
        }).collect(Collectors.toList());
    }

    private long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private LocalDate getPeriodStart(String period) {
        LocalDate today = LocalDate.now();
        String normalized = period == null ? "year" : period.toLowerCase();
        return switch (normalized) {
            case "week" -> today.minusWeeks(1);
            case "quarter" -> today.minusMonths(3);
            case "year" -> today.minusYears(1);
            case "all" -> LocalDate.of(1900, 1, 1);
            default -> today.withDayOfMonth(1); // month
        };
    }

    private boolean isWithinPeriod(LocalDate date, LocalDate start, LocalDate end) {
        if (date == null) return true;
        return !date.isBefore(start) && !date.isAfter(end);
    }

    private LocalDate assetDate(Asset asset) {
        if (asset.getPurchaseDate() != null) return asset.getPurchaseDate();
        return toLocalDate(asset.getCreatedAt());
    }

    private LocalDate maintenanceDate(MaintenanceRecord record) {
        if (record.getPerformedDate() != null) return record.getPerformedDate();
        if (record.getScheduledDate() != null) return record.getScheduledDate();
        return toLocalDate(record.getCreatedAt());
    }

    private LocalDate toLocalDate(Instant instant) {
        return instant == null ? null : instant.atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
