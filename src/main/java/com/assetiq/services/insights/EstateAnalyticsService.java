package com.assetiq.services.insights;

import com.assetiq.models.Organisation;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.services.finance.DepreciationCalculator;
import com.assetiq.services.money.CurrencyConversion;
import com.assetiq.services.money.MoneyAccumulator;
import com.assetiq.services.money.MoneyAggregator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "What is my estate worth, and where is it?"
 *
 * <p>Answers with cost, accumulated depreciation and net book value broken down
 * by department, location, category, status or condition — every figure in the
 * tenant's base currency, and every group carrying the identifier the UI needs
 * to link through to the assets behind the number.
 *
 * <p>Cost: one query ({@code AssetRepository#findValuationRows}) and one
 * depreciation calculation per asset, in memory, with no further I/O. The
 * calculation is pure arithmetic over a slim row; the dominant cost is the
 * single table scan, which an index on {@code (organisation_id, deleted_at)}
 * keeps proportional to the tenant rather than to the platform.
 */
@Service
@Transactional(readOnly = true)
public class EstateAnalyticsService {

    /** Dimensions a caller may break the estate down by. */
    public enum Dimension {
        DEPARTMENT("departmentId"),
        LOCATION("locationId"),
        CATEGORY("categoryId"),
        STATUS("status"),
        CONDITION("condition");

        private final String filterKey;

        Dimension(String filterKey) {
            this.filterKey = filterKey;
        }

        public String filterKey() {
            return filterKey;
        }

        static Dimension parse(String raw) {
            String value = raw == null || raw.isBlank() ? "DEPARTMENT" : raw.trim().toUpperCase(Locale.ROOT);
            for (Dimension d : values()) {
                if (d.name().equals(value)) {
                    return d;
                }
            }
            throw new IllegalArgumentException(
                    "Invalid groupBy '" + raw + "'. Allowed values: department, location, category, status, condition");
        }

        /** The section a caller must be able to read for this breakdown to be named. */
        InsightSection namingSection() {
            return switch (this) {
                case DEPARTMENT -> InsightSection.DEPARTMENTS;
                case LOCATION -> InsightSection.LOCATIONS;
                default -> InsightSection.ASSETS;
            };
        }
    }

    private final AssetRepository assetRepository;
    private final MoneyAggregator moneyAggregator;

    public EstateAnalyticsService(AssetRepository assetRepository, MoneyAggregator moneyAggregator) {
        this.assetRepository = assetRepository;
        this.moneyAggregator = moneyAggregator;
    }

    /**
     * Value the tenant's on-book estate, grouped by {@code groupBy}.
     *
     * @param granted the sections this caller may read; a breakdown whose naming
     *                dimension is withheld falls back to totals only
     */
    public Map<String, Object> estate(Organisation org, String groupBy, Set<InsightSection> granted) {
        Dimension dimension = Dimension.parse(groupBy);
        LocalDate asOf = LocalDate.now();
        CurrencyConversion fx = moneyAggregator.begin(org, asOf);

        Set<InsightSection> required = EnumSet.of(InsightSection.ASSETS, InsightSection.VALUATION,
                dimension.namingSection());

        Map<String, Object> response = InsightResponse.envelope(fx, asOf, required, granted);
        response.put("groupBy", dimension.name().toLowerCase(Locale.ROOT));

        if (!granted.contains(InsightSection.ASSETS)) {
            response.put("assetCount", 0L);
            response.put("groups", List.of());
            return InsightResponse.finish(response, fx);
        }

        boolean showMoney = granted.contains(InsightSection.VALUATION);
        boolean showGroups = granted.contains(dimension.namingSection());

        List<AssetValuationRow> rows = assetRepository.findValuationRows(org).stream()
                .filter(AssetValuationRow::onBooks)
                .toList();

        Bucket total = new Bucket(fx, null, "All assets");
        Map<String, Bucket> buckets = new LinkedHashMap<>();

        for (AssetValuationRow row : rows) {
            DepreciationCalculator.Result depreciation = row.depreciation(asOf);
            total.add(row, depreciation);
            if (showGroups) {
                GroupKey key = keyOf(row, dimension);
                buckets.computeIfAbsent(key.id(), k -> new Bucket(fx, key.rawId(), key.name(), key.filterValue()))
                        .add(row, depreciation);
            }
        }

        response.put("assetCount", total.count);
        if (showMoney) {
            total.putTotals(response);
        }

        List<Map<String, Object>> groups = new ArrayList<>();
        if (showGroups) {
            BigDecimal costTotal = total.cost.rawSum();
            buckets.values().stream()
                    .sorted(Comparator.comparing((Bucket b) -> b.cost.rawSum()).reversed()
                            .thenComparing(b -> b.name))
                    .forEach(b -> groups.add(b.toMap(dimension, costTotal, total.count, showMoney)));
        }
        response.put("groups", groups);
        return InsightResponse.finish(response, fx);
    }

    // ── Grouping ──────────────────────────────────────────────────────────────

    /**
     * @param id          de-duplication key for the group
     * @param rawId       the entity id the UI links on, or null for "unassigned"
     * @param name        display name
     * @param filterValue what to send back as a filter, or null when the group is
     *                    exactly the records that have no value for this dimension
     */
    private record GroupKey(String id, UUID rawId, String name, String filterValue) {
    }

    private GroupKey keyOf(AssetValuationRow row, Dimension dimension) {
        return switch (dimension) {
            case DEPARTMENT -> reference(row.departmentId(), row.departmentName(), "No department");
            case LOCATION -> reference(row.locationId(), row.locationName(), "No location");
            case CATEGORY -> reference(row.categoryId(), row.categoryName(), "Uncategorised");
            case STATUS -> enumKey(row.status() == null ? null : row.status().name(), "Unknown status");
            case CONDITION -> enumKey(row.condition() == null ? null : row.condition().name(), "Unknown condition");
        };
    }

    private GroupKey reference(UUID id, String name, String unassignedLabel) {
        if (id == null) {
            return new GroupKey("\u0000unassigned", null, unassignedLabel, null);
        }
        return new GroupKey(id.toString(), id, name != null ? name : unassignedLabel, id.toString());
    }

    private GroupKey enumKey(String value, String unknownLabel) {
        if (value == null) {
            return new GroupKey("\u0000unknown", null, unknownLabel, null);
        }
        return new GroupKey(value, null, value, value);
    }

    /** One group's running totals, all converted through the caller's pass. */
    private static final class Bucket {
        private final UUID id;
        private final String name;
        private final String filterValue;
        private final MoneyAccumulator cost;
        private final MoneyAccumulator netBookValue;
        private final MoneyAccumulator accumulatedDepreciation;
        private final MoneyAccumulator monthlyDepreciation;
        private long count;
        private long fullyDepreciated;
        private long missingSetup;

        Bucket(CurrencyConversion fx, UUID id, String name) {
            this(fx, id, name, null);
        }

        Bucket(CurrencyConversion fx, UUID id, String name, String filterValue) {
            this.id = id;
            this.name = name;
            this.filterValue = filterValue;
            this.cost = fx.newAccumulator();
            this.netBookValue = fx.newAccumulator();
            this.accumulatedDepreciation = fx.newAccumulator();
            this.monthlyDepreciation = fx.newAccumulator();
        }

        void add(AssetValuationRow row, DepreciationCalculator.Result r) {
            count++;
            String currency = row.currency();
            cost.add(row.purchaseCost(), currency);
            netBookValue.add(r.netBookValue(), currency);
            accumulatedDepreciation.add(r.accumulatedDepreciation(), currency);
            monthlyDepreciation.add(r.monthlyDepreciation(), currency);
            if (row.purchaseCost() != null) {
                if (!r.configured()) {
                    missingSetup++;
                } else if (r.fullyDepreciated()) {
                    fullyDepreciated++;
                }
            }
        }

        void putTotals(Map<String, Object> target) {
            target.put("totalCost", cost.amount());
            target.put("accumulatedDepreciation", accumulatedDepreciation.amount());
            target.put("netBookValue", netBookValue.amount());
            target.put("monthlyDepreciation", monthlyDepreciation.amount());
            target.put("assetsFullyDepreciated", fullyDepreciated);
            target.put("assetsMissingDepreciationSetup", missingSetup);
        }

        Map<String, Object> toMap(Dimension dimension, BigDecimal costTotal, long countTotal, boolean showMoney) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", name);
            m.put("count", count);
            m.put("countShare", InsightResponse.percentage(count, countTotal));
            if (showMoney) {
                m.put("cost", cost.amount());
                m.put("accumulatedDepreciation", accumulatedDepreciation.amount());
                m.put("netBookValue", netBookValue.amount());
                m.put("monthlyDepreciation", monthlyDepreciation.amount());
                m.put("costShare", InsightResponse.percentage(cost.rawSum(), costTotal));
                m.put("assetsFullyDepreciated", fullyDepreciated);
            }
            // What the UI should send to /api/v1/assets to show the rows behind this
            // number. A null value means "the records with no value for this
            // dimension", which is a real, linkable set — not a missing filter.
            Map<String, Object> filter = new LinkedHashMap<>();
            filter.put(dimension.filterKey(), filterValue);
            m.put("filter", filter);
            return m;
        }
    }
}
