package com.assetiq.services.insights;

import com.assetiq.models.AnalyticsSnapshot;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.AnalyticsSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Trends read from recorded history, never reconstructed.
 *
 * <p>The honesty rules here matter more than the arithmetic. A tenant that
 * installed AssetIQ last Tuesday has four data points, and the response says so:
 * {@code historyDays}, {@code firstSnapshot} and {@code sufficientHistory} are
 * part of the payload, there is no smoothing, no extrapolation, and the change
 * figures are null rather than zero when there is nothing to compare against.
 * A flat line the user can see is honest; a projected line they cannot audit is
 * not.
 *
 * <p>Cost: one index range scan over {@code analytics_snapshot} bounded by the
 * requested window — one row per day per tenant, so a two-year trend is 730
 * narrow rows.
 */
@Service
@Transactional(readOnly = true)
public class TrendService {

    public static final int DEFAULT_DAYS = 90;
    public static final int MAX_DAYS = 1095;
    /** Below this many points a trend is a couple of dots, and is labelled as such. */
    private static final int MIN_POINTS_FOR_A_TREND = 7;

    private final AnalyticsSnapshotRepository snapshotRepository;

    public TrendService(AnalyticsSnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;
    }

    public Map<String, Object> trends(Organisation org, Integer days, Set<InsightSection> granted) {
        int window = days == null ? DEFAULT_DAYS : days;
        if (window < 1 || window > MAX_DAYS) {
            throw new IllegalArgumentException("days must be between 1 and " + MAX_DAYS);
        }
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(window);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("asOf", today.toString());
        response.put("days", window);
        Set<InsightSection> required = EnumSet.of(InsightSection.ASSETS, InsightSection.VALUATION);
        response.put("withheldSections", InsightResponse.withheld(required, granted));

        if (!granted.contains(InsightSection.ASSETS)) {
            response.put("points", List.of());
            response.put("sufficientHistory", false);
            response.put("note", "Trends need permission to read assets.");
            return InsightResponse.generatedNow(response);
        }

        boolean showMoney = granted.contains(InsightSection.VALUATION);
        List<AnalyticsSnapshot> snapshots = snapshotRepository.findSince(org, from);
        Optional<LocalDate> earliest = snapshotRepository.findEarliestSnapshotDate(org);

        response.put("firstSnapshot", earliest.map(LocalDate::toString).orElse(null));
        response.put("historyDays", earliest.map(d -> ChronoUnit.DAYS.between(d, today) + 1).orElse(0L));
        response.put("pointCount", snapshots.size());

        boolean sufficient = snapshots.size() >= MIN_POINTS_FOR_A_TREND;
        response.put("sufficientHistory", sufficient);
        if (snapshots.isEmpty()) {
            response.put("note", "No history has been recorded yet. The first snapshot is taken overnight; "
                    + "trends appear from the following day.");
        } else if (!sufficient) {
            response.put("note", "Only " + snapshots.size() + " day(s) of history exist, so this shows the "
                    + "points recorded so far rather than a trend. Nothing has been estimated or filled in.");
        }

        // The currencies the series is expressed in. More than one means the tenant
        // changed base currency mid-series; the UI must break the line, not average it.
        List<String> currencies = snapshots.stream().map(AnalyticsSnapshot::getCurrency).distinct().toList();
        response.put("currencies", currencies);
        response.put("currency", currencies.size() == 1 ? currencies.get(0) : null);
        response.put("currencyChanged", currencies.size() > 1);
        response.put("anyIncomplete", snapshots.stream().anyMatch(s -> !s.isComplete()));

        List<Map<String, Object>> points = new ArrayList<>();
        for (AnalyticsSnapshot s : snapshots) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("date", s.getSnapshotDate().toString());
            p.put("assetCount", s.getAssetCount());
            p.put("activeAssetCount", s.getActiveAssetCount());
            p.put("idleAssetCount", s.getIdleAssetCount());
            p.put("unassignedInUseCount", s.getUnassignedInUseCount());
            p.put("fullyDepreciatedCount", s.getFullyDepreciatedCount());
            p.put("overdueMaintenanceCount", s.getOverdueMaintenanceCount());
            p.put("licenceSeatsTotal", s.getLicenceSeatsTotal());
            p.put("licenceSeatsUsed", s.getLicenceSeatsUsed());
            if (showMoney) {
                p.put("totalCost", s.getTotalCost());
                p.put("netBookValue", s.getNetBookValue());
                p.put("accumulatedDepreciation", s.getAccumulatedDepreciation());
                p.put("monthlyDepreciation", s.getMonthlyDepreciation());
                p.put("currency", s.getCurrency());
                p.put("complete", s.isComplete());
            }
            points.add(p);
        }
        response.put("points", points);
        response.put("change", change(snapshots, showMoney, currencies.size() == 1));
        return InsightResponse.generatedNow(response);
    }

    /**
     * First point against last point. Null throughout when there is only one
     * point — "no change" and "nothing to compare" are different answers, and
     * money deltas are suppressed entirely when the base currency changed
     * mid-series, because subtracting two different units is meaningless.
     */
    private Map<String, Object> change(List<AnalyticsSnapshot> snapshots, boolean showMoney, boolean oneCurrency) {
        Map<String, Object> change = new LinkedHashMap<>();
        if (snapshots.size() < 2) {
            change.put("comparable", false);
            return change;
        }
        AnalyticsSnapshot first = snapshots.get(0);
        AnalyticsSnapshot last = snapshots.get(snapshots.size() - 1);
        change.put("comparable", true);
        change.put("from", first.getSnapshotDate().toString());
        change.put("to", last.getSnapshotDate().toString());
        change.put("assetCount", last.getAssetCount() - first.getAssetCount());
        change.put("activeAssetCount", last.getActiveAssetCount() - first.getActiveAssetCount());
        change.put("idleAssetCount", last.getIdleAssetCount() - first.getIdleAssetCount());
        change.put("unassignedInUseCount", last.getUnassignedInUseCount() - first.getUnassignedInUseCount());
        change.put("overdueMaintenanceCount",
                last.getOverdueMaintenanceCount() - first.getOverdueMaintenanceCount());
        change.put("licenceSeatsUsed", last.getLicenceSeatsUsed() - first.getLicenceSeatsUsed());
        if (showMoney && oneCurrency) {
            change.put("totalCost", delta(last.getTotalCost(), first.getTotalCost()));
            change.put("netBookValue", delta(last.getNetBookValue(), first.getNetBookValue()));
            change.put("currency", last.getCurrency());
        } else if (showMoney) {
            change.put("moneyComparable", false);
            change.put("note", "The base currency changed during this window, so money cannot be compared.");
        }
        return change;
    }

    private BigDecimal delta(BigDecimal to, BigDecimal from) {
        if (to == null || from == null) {
            return null;
        }
        return to.subtract(from);
    }
}
