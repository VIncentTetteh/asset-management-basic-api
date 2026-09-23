package com.assetiq.services.insights;

import com.assetiq.enums.AssetCondition;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.LicenseStatus;
import com.assetiq.models.Organisation;
import com.assetiq.models.SoftwareLicense;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.SoftwareLicenseRepository;
import com.assetiq.services.finance.DepreciationCalculator;
import com.assetiq.services.money.CurrencyConversion;
import com.assetiq.services.money.MoneyAccumulator;
import com.assetiq.services.money.MoneyAggregator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "What is costing me money that I am not using?"
 *
 * <p>Each finding is a set of records a facilities or IT manager can act on
 * today — redeploy it, dispose of it, or drop the seats at renewal — never a
 * score. Every finding names the records behind it so the number can be opened,
 * and states in words what it counted, because "waste" is a judgement and the
 * user is entitled to disagree with the rule rather than with a total.
 *
 * <p>Findings deliberately overlap (an asset can be both idle and fully
 * depreciated), so there is no grand total that adds them up. What is reported
 * instead is {@code distinctAssetsFlagged} and {@code capitalTiedUp} —
 * de-duplicated by asset — plus {@code licenceAnnualSavings}, which is the one
 * figure here that is genuinely recurring cash.
 *
 * <p>Cost: one asset projection query plus one licence query. No per-record I/O.
 */
@Service
@Transactional(readOnly = true)
public class CostWasteService {

    /** Default idleness threshold; a quarter of neglect is a decision, not an oversight. */
    public static final int DEFAULT_IDLE_DAYS = 180;
    public static final int MAX_IDLE_DAYS = 3650;
    /** Records listed under each finding, so a response stays a page, not a dump. */
    public static final int DEFAULT_ITEM_LIMIT = 10;
    public static final int MAX_ITEM_LIMIT = 100;

    private final AssetRepository assetRepository;
    private final SoftwareLicenseRepository licenseRepository;
    private final MoneyAggregator moneyAggregator;

    public CostWasteService(AssetRepository assetRepository,
                            SoftwareLicenseRepository licenseRepository,
                            MoneyAggregator moneyAggregator) {
        this.assetRepository = assetRepository;
        this.licenseRepository = licenseRepository;
        this.moneyAggregator = moneyAggregator;
    }

    public Map<String, Object> costWaste(Organisation org, Integer idleDays, Integer itemLimit,
                                         Set<InsightSection> granted) {
        int idle = bounded(idleDays, DEFAULT_IDLE_DAYS, 1, MAX_IDLE_DAYS);
        int limit = bounded(itemLimit, DEFAULT_ITEM_LIMIT, 1, MAX_ITEM_LIMIT);
        LocalDate asOf = LocalDate.now();
        CurrencyConversion fx = moneyAggregator.begin(org, asOf);

        Set<InsightSection> required = EnumSet.of(InsightSection.ASSETS, InsightSection.VALUATION,
                InsightSection.LICENCES);
        Map<String, Object> response = InsightResponse.envelope(fx, asOf, required, granted);
        response.put("idleDays", idle);

        List<Map<String, Object>> findings = new ArrayList<>();
        Set<UUID> flagged = new HashSet<>();
        MoneyAccumulator capitalTiedUp = fx.newAccumulator();
        MoneyAccumulator licenceSavings = fx.newAccumulator();

        boolean showMoney = granted.contains(InsightSection.VALUATION);

        if (granted.contains(InsightSection.ASSETS)) {
            assetFindings(org, asOf, fx, idle, limit, showMoney, findings, flagged, capitalTiedUp);
        }
        if (granted.contains(InsightSection.LICENCES)) {
            licenceFindings(org, fx, limit, showMoney, findings, licenceSavings);
        }

        response.put("distinctAssetsFlagged", (long) flagged.size());
        if (showMoney) {
            response.put("capitalTiedUp", capitalTiedUp.amount());
            response.put("licenceAnnualSavings", licenceSavings.amount());
        }
        response.put("findings", findings);
        return InsightResponse.finish(response, fx);
    }

    // ── Asset findings ────────────────────────────────────────────────────────

    private void assetFindings(Organisation org, LocalDate asOf, CurrencyConversion fx,
                               int idleDays, int limit, boolean showMoney,
                               List<Map<String, Object>> findings, Set<UUID> flagged,
                               MoneyAccumulator capitalTiedUp) {
        List<AssetValuationRow> rows = assetRepository.findValuationRows(org);
        Instant idleCutoff = Instant.now().minus(Duration.ofDays(idleDays));

        Finding fullyDepreciated = new Finding(fx,
                "FULLY_DEPRECIATED_ACTIVE",
                "Fully depreciated but still in service",
                "Assets whose useful life has elapsed and which are still in use, in stock or reserved. "
                        + "They have no book value left to lose, so the next failure is an unplanned "
                        + "purchase rather than a planned one.",
                Map.of("fullyDepreciated", "true"));

        Finding idle = new Finding(fx,
                "IDLE_IN_STOCK",
                "In stock or reserved and untouched for " + idleDays + "+ days",
                "Assets held in stock or reserved that nobody has scanned or edited for "
                        + idleDays + " days. Idleness is inferred from the last scan, falling back to the "
                        + "last edit — AssetIQ records no usage telemetry, so this says 'nobody has "
                        + "touched the record', not 'nobody has used the thing'.",
                Map.of("status", "IN_STOCK"));

        Finding unassigned = new Finding(fx,
                "UNASSIGNED_IN_USE",
                "Marked in use with nobody assigned",
                "Assets whose status says they are in use but which have no assigned holder. "
                        + "Either someone has them and the register is wrong, or nobody does and they "
                        + "are idle. Both are worth an hour of somebody's time.",
                Map.of("status", "IN_USE", "assigned", "false"));

        Finding missing = new Finding(fx,
                "MISSING",
                "Missing and still on the books",
                "Assets marked missing that have not been written off. Their remaining book value "
                        + "is an unrecognised loss sitting in the fixed asset register.",
                Map.of("status", "MISSING"));

        Finding unusable = new Finding(fx,
                "UNUSABLE_CONDITION",
                "Damaged or scrap, still carried",
                "Assets in damaged or scrap condition that have not been disposed of. They carry "
                        + "book value, may carry insurance, and are almost certainly not working.",
                Map.of("condition", "DAMAGED"));

        for (AssetValuationRow row : rows) {
            if (!row.onBooks()) {
                continue;
            }
            DepreciationCalculator.Result depreciation = row.depreciation(asOf);

            if (row.active() && depreciation.configured() && depreciation.fullyDepreciated()) {
                LocalDate endOfLife = row.purchaseDate() != null && depreciation.usefulLifeMonths() != null
                        ? row.purchaseDate().plusMonths(depreciation.usefulLifeMonths()) : null;
                fullyDepreciated.add(row, depreciation, endOfLife == null
                        ? "Useful life elapsed"
                        : "Useful life ended " + endOfLife);
            }

            if ((row.status() == AssetStatus.IN_STOCK || row.status() == AssetStatus.RESERVED)) {
                Instant touched = row.lastTouchedAt();
                if (touched != null && touched.isBefore(idleCutoff)) {
                    long days = ChronoUnit.DAYS.between(touched, Instant.now());
                    idle.add(row, depreciation, (row.lastScannedAt() != null ? "Last scanned " : "Last edited ")
                            + days + " days ago");
                }
            }

            if (row.status() == AssetStatus.IN_USE && row.assignedUserId() == null) {
                unassigned.add(row, depreciation, "No assigned holder");
            }

            if (row.status() == AssetStatus.MISSING) {
                missing.add(row, depreciation, "Marked missing");
            }

            if (row.status() != AssetStatus.RETIRED
                    && (row.condition() == AssetCondition.DAMAGED || row.condition() == AssetCondition.SCRAP)) {
                unusable.add(row, depreciation, "Condition: " + row.condition().name());
            }
        }

        for (Finding f : List.of(fullyDepreciated, idle, unassigned, missing, unusable)) {
            findings.add(f.toMap(limit, showMoney));
            f.contributeDistinct(flagged, capitalTiedUp);
        }
    }

    // ── Licence findings ──────────────────────────────────────────────────────

    private void licenceFindings(Organisation org, CurrencyConversion fx, int limit, boolean showMoney,
                                 List<Map<String, Object>> findings, MoneyAccumulator licenceSavings) {
        List<SoftwareLicense> licences = licenseRepository.findByOrganisationAndDeletedAtIsNull(org);

        List<Map<String, Object>> unusedItems = new ArrayList<>();
        List<Map<String, Object>> overItems = new ArrayList<>();
        MoneyAccumulator unusedCost = fx.newAccumulator();
        MoneyAccumulator overExposure = fx.newAccumulator();
        long unusedSeatTotal = 0;
        long excessSeatTotal = 0;
        long unusedCount = 0;
        long overCount = 0;

        for (SoftwareLicense licence : licences) {
            if (licence.getStatus() == LicenseStatus.CANCELLED || licence.getStatus() == LicenseStatus.EXPIRED) {
                continue;
            }
            Integer total = licence.getTotalSeats();
            Integer used = licence.getUsedSeats();
            if (total == null || total <= 0) {
                continue;
            }
            int usedSeats = used == null ? 0 : used;

            if (usedSeats < total) {
                int spare = total - usedSeats;
                unusedSeatTotal += spare;
                unusedCount++;
                BigDecimal perSeatYear = perSeatAnnualCost(licence, total);
                BigDecimal wasted = perSeatYear.multiply(BigDecimal.valueOf(spare));
                unusedCost.add(wasted, licence.getCurrency());
                licenceSavings.add(wasted, licence.getCurrency());
                unusedItems.add(licenceItem(licence, spare + " of " + total + " seats unused",
                        fx, wasted, showMoney));
            } else if (usedSeats > total) {
                int excess = usedSeats - total;
                excessSeatTotal += excess;
                overCount++;
                BigDecimal perSeatYear = perSeatAnnualCost(licence, total);
                BigDecimal exposure = perSeatYear.multiply(BigDecimal.valueOf(excess));
                overExposure.add(exposure, licence.getCurrency());
                overItems.add(licenceItem(licence, excess + " seats beyond the " + total + " purchased",
                        fx, exposure, showMoney));
            }
        }

        Map<String, Object> unused = new LinkedHashMap<>();
        unused.put("key", "LICENCE_UNUSED_SEATS");
        unused.put("title", "Software seats paid for and not used");
        unused.put("explanation", "Active licences with fewer seats in use than purchased. The cost shown is "
                + "the annual renewal cost apportioned per seat over the unused seats; where a licence has no "
                + "annual renewal cost recorded, its purchase cost is apportioned instead and it is a one-off, "
                + "not a yearly saving.");
        unused.put("resourceType", "licence");
        unused.put("count", unusedCount);
        unused.put("unusedSeats", unusedSeatTotal);
        if (showMoney) {
            unused.put("annualCost", unusedCost.amount());
        }
        unused.put("filter", Map.of("underUtilised", "true"));
        unused.put("items", truncate(unusedItems, limit));
        unused.put("truncated", unusedItems.size() > limit);
        findings.add(unused);

        Map<String, Object> over = new LinkedHashMap<>();
        over.put("key", "LICENCE_OVER_ALLOCATED");
        over.put("title", "Software in use beyond the seats purchased");
        over.put("explanation", "Active licences with more seats in use than purchased. This is a compliance "
                + "exposure, not a saving: the figure is what the extra seats would cost for a year at the "
                + "licence's own per-seat rate.");
        over.put("resourceType", "licence");
        over.put("count", overCount);
        over.put("excessSeats", excessSeatTotal);
        if (showMoney) {
            over.put("annualExposure", overExposure.amount());
        }
        over.put("filter", Map.of("overAllocated", "true"));
        over.put("items", truncate(overItems, limit));
        over.put("truncated", overItems.size() > limit);
        findings.add(over);
    }

    /**
     * Annual cost of one seat: the renewal cost where there is one, otherwise the
     * purchase cost (which is a one-off — the caller's explanation says so).
     */
    private BigDecimal perSeatAnnualCost(SoftwareLicense licence, int totalSeats) {
        BigDecimal basis = licence.getAnnualRenewalCost() != null
                ? licence.getAnnualRenewalCost() : licence.getPurchaseCost();
        if (basis == null || totalSeats <= 0) {
            return BigDecimal.ZERO;
        }
        return basis.divide(BigDecimal.valueOf(totalSeats), 6, RoundingMode.HALF_UP);
    }

    private Map<String, Object> licenceItem(SoftwareLicense licence, String detail,
                                            CurrencyConversion fx, BigDecimal amount, boolean showMoney) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", licence.getId());
        m.put("name", licence.getName());
        m.put("reference", licence.getVendor());
        m.put("resourceType", "licence");
        m.put("totalSeats", licence.getTotalSeats());
        m.put("usedSeats", licence.getUsedSeats());
        m.put("expiryDate", licence.getExpiryDate() == null ? null : licence.getExpiryDate().toString());
        if (showMoney) {
            // Left null rather than zero when the licence's currency has no rate:
            // an unconvertible amount is unknown, not free.
            m.put("amount", fx.toBase(amount, licence.getCurrency())
                    .map(CurrencyConversion::round).orElse(null));
            m.put("nativeCurrency", licence.getCurrency());
        }
        m.put("detail", detail);
        return m;
    }

    // ── Plumbing ──────────────────────────────────────────────────────────────

    private static int bounded(Integer value, int fallback, int min, int max) {
        if (value == null) {
            return fallback;
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException("Value must be between " + min + " and " + max);
        }
        return value;
    }

    private static <T> List<T> truncate(List<T> items, int limit) {
        return items.size() <= limit ? List.copyOf(items) : List.copyOf(items.subList(0, limit));
    }

    /** One asset-based finding: its members, its money, and the rows to show. */
    private static final class Finding {
        private final CurrencyConversion fx;
        private final String key;
        private final String title;
        private final String explanation;
        private final Map<String, String> filter;
        private final MoneyAccumulator cost;
        private final MoneyAccumulator netBookValue;
        private final List<Entry> entries = new ArrayList<>();

        private record Entry(AssetValuationRow row, DepreciationCalculator.Result depreciation, String detail) {
        }

        Finding(CurrencyConversion fx, String key, String title, String explanation, Map<String, String> filter) {
            this.fx = fx;
            this.key = key;
            this.title = title;
            this.explanation = explanation;
            this.filter = filter;
            this.cost = fx.newAccumulator();
            this.netBookValue = fx.newAccumulator();
        }

        void add(AssetValuationRow row, DepreciationCalculator.Result depreciation, String detail) {
            entries.add(new Entry(row, depreciation, detail));
            cost.add(row.purchaseCost(), row.currency());
            netBookValue.add(depreciation.netBookValue(), row.currency());
        }

        /** Add this finding's assets to the de-duplicated totals for the response. */
        void contributeDistinct(Set<UUID> flagged, MoneyAccumulator capitalTiedUp) {
            for (Entry e : entries) {
                if (flagged.add(e.row().id())) {
                    capitalTiedUp.add(e.depreciation().netBookValue(), e.row().currency());
                }
            }
        }

        Map<String, Object> toMap(int limit, boolean showMoney) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", key);
            m.put("title", title);
            m.put("explanation", explanation);
            m.put("resourceType", "asset");
            m.put("count", (long) entries.size());
            if (showMoney) {
                m.put("originalCost", cost.amount());
                m.put("netBookValue", netBookValue.amount());
            }
            m.put("filter", filter);
            List<Map<String, Object>> items = entries.stream()
                    // Biggest book value first: the ones worth acting on today.
                    .sorted(Comparator.comparing(
                            (Entry e) -> e.depreciation().netBookValue() == null
                                    ? BigDecimal.ZERO : e.depreciation().netBookValue())
                            .reversed())
                    .limit(limit)
                    .map(e -> item(e, showMoney))
                    .toList();
            m.put("items", items);
            m.put("truncated", entries.size() > limit);
            return m;
        }

        private Map<String, Object> item(Entry e, boolean showMoney) {
            AssetValuationRow row = e.row();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", row.id());
            m.put("name", row.name());
            m.put("reference", row.assetTag());
            m.put("resourceType", "asset");
            m.put("status", row.status() == null ? null : row.status().name());
            m.put("departmentId", row.departmentId());
            m.put("departmentName", row.departmentName());
            m.put("locationId", row.locationId());
            m.put("locationName", row.locationName());
            if (showMoney) {
                m.put("cost", fx.toBase(row.purchaseCost(), row.currency())
                        .map(CurrencyConversion::round).orElse(null));
                m.put("netBookValue", fx.toBase(e.depreciation().netBookValue(), row.currency())
                        .map(CurrencyConversion::round).orElse(null));
                m.put("nativeCurrency", row.currency());
            }
            m.put("detail", e.detail());
            return m;
        }
    }
}
