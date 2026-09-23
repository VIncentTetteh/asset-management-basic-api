package com.assetiq.services.insights;

import com.assetiq.models.Organisation;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.ContractRepository;
import com.assetiq.repositories.LeaseRecordRepository;
import com.assetiq.repositories.MaintenanceRecordRepository;
import com.assetiq.repositories.SoftwareLicenseRepository;
import com.assetiq.services.money.CurrencyConversion;
import com.assetiq.services.money.MoneyAccumulator;
import com.assetiq.services.money.MoneyAggregator;
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
import java.util.Set;
import java.util.function.BiFunction;

/**
 * "What is about to bite me?"
 *
 * <p>Six dated commitments — warranties, insurance, maintenance, contracts,
 * licences and leases — gathered into one radar, bucketed by how soon they land,
 * and each carrying the records behind it.
 *
 * <p>Every stream is queried with its own date bound, so the work is
 * proportional to what is actually expiring rather than to the size of the
 * estate: six index range scans, each tenant-scoped, none of them loading an
 * entity. A tenant with fifty thousand assets and nothing expiring pays for six
 * empty scans.
 *
 * <p>A stream the caller may not read is omitted entirely and named in
 * {@code withheldSections}. It is never shown as zero — "no contracts expiring"
 * and "you may not see contracts" are different answers.
 */
@Service
@Transactional(readOnly = true)
public class ExpiryRadarService {

    public static final int DEFAULT_HORIZON_DAYS = 90;
    public static final int MAX_HORIZON_DAYS = 730;
    public static final int DEFAULT_ITEM_LIMIT = 10;
    public static final int MAX_ITEM_LIMIT = 100;

    private static final int BUCKET_WIDTH_DAYS = 30;

    private final AssetRepository assetRepository;
    private final MaintenanceRecordRepository maintenanceRepository;
    private final ContractRepository contractRepository;
    private final SoftwareLicenseRepository licenseRepository;
    private final LeaseRecordRepository leaseRepository;
    private final MoneyAggregator moneyAggregator;

    public ExpiryRadarService(AssetRepository assetRepository,
                              MaintenanceRecordRepository maintenanceRepository,
                              ContractRepository contractRepository,
                              SoftwareLicenseRepository licenseRepository,
                              LeaseRecordRepository leaseRepository,
                              MoneyAggregator moneyAggregator) {
        this.assetRepository = assetRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.contractRepository = contractRepository;
        this.licenseRepository = licenseRepository;
        this.leaseRepository = leaseRepository;
        this.moneyAggregator = moneyAggregator;
    }

    public Map<String, Object> radar(Organisation org, Integer horizonDays, Integer itemLimit,
                                     Set<InsightSection> granted) {
        int horizon = bounded(horizonDays, DEFAULT_HORIZON_DAYS, 1, MAX_HORIZON_DAYS);
        int limit = bounded(itemLimit, DEFAULT_ITEM_LIMIT, 1, MAX_ITEM_LIMIT);
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(horizon);
        CurrencyConversion fx = moneyAggregator.begin(org, today);

        Set<InsightSection> required = EnumSet.of(InsightSection.ASSETS, InsightSection.MAINTENANCE,
                InsightSection.CONTRACTS, InsightSection.LICENCES, InsightSection.LEASES);
        Map<String, Object> response = InsightResponse.envelope(fx, today, required, granted);
        response.put("horizonDays", horizon);
        response.put("horizonEnd", cutoff.toString());

        boolean showMoney = granted.contains(InsightSection.VALUATION);

        List<Stream> streams = List.of(
                new Stream("WARRANTY", "Asset warranties", InsightSection.ASSETS, "asset",
                        "original purchase cost of the asset losing cover",
                        (o, c) -> assetRepository.findWarrantyDueBy(o, c)),
                new Stream("INSURANCE", "Asset insurance policies", InsightSection.ASSETS, "asset",
                        "annual premium, falling back to the asset's purchase cost when no premium is recorded",
                        (o, c) -> assetRepository.findInsuranceDueBy(o, c)),
                new Stream("MAINTENANCE", "Scheduled maintenance", InsightSection.MAINTENANCE, "maintenance",
                        "cost of the maintenance job as scheduled",
                        (o, c) -> maintenanceRepository.findDueBy(o, c)),
                new Stream("CONTRACT", "Supplier contracts", InsightSection.CONTRACTS, "contract",
                        "total contract value",
                        (o, c) -> contractRepository.findDueBy(o, c)),
                new Stream("LICENCE", "Software licences", InsightSection.LICENCES, "licence",
                        "annual renewal cost, falling back to the purchase cost when no renewal cost is recorded",
                        (o, c) -> licenseRepository.findDueBy(o, c)),
                new Stream("LEASE", "Equipment leases", InsightSection.LEASES, "lease",
                        "monthly payment that continues if the notice period is missed",
                        (o, c) -> leaseRepository.findDueBy(o, c)));

        List<Map<String, Object>> rendered = new ArrayList<>();
        long overdueTotal = 0;
        long dueTotal = 0;

        for (Stream stream : streams) {
            if (!granted.contains(stream.section())) {
                continue;
            }
            List<DueRow> rows = stream.query().apply(org, cutoff);
            Rendered r = render(stream, rows, today, horizon, limit, fx, showMoney);
            overdueTotal += r.overdue();
            dueTotal += r.upcoming();
            rendered.add(r.payload());
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("overdue", overdueTotal);
        totals.put("dueWithinHorizon", dueTotal);
        response.put("totals", totals);
        response.put("streams", rendered);
        return InsightResponse.finish(response, fx);
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private record Stream(String key, String label, InsightSection section, String resourceType,
                          String valueMeaning,
                          BiFunction<Organisation, LocalDate, List<DueRow>> query) {
    }

    private record Rendered(Map<String, Object> payload, long overdue, long upcoming) {
    }

    private Rendered render(Stream stream, List<DueRow> rows, LocalDate today, int horizon,
                            int limit, CurrencyConversion fx, boolean showMoney) {
        Map<String, Bucket> buckets = new LinkedHashMap<>();
        buckets.put("OVERDUE", new Bucket(fx, "OVERDUE", "Already past due", null, -1));
        for (int start = 0; start < horizon; start += BUCKET_WIDTH_DAYS) {
            int end = Math.min(start + BUCKET_WIDTH_DAYS - 1, horizon);
            String key = "DUE_" + start + "_" + end;
            buckets.put(key, new Bucket(fx, key, "Due in " + start + "-" + end + " days", start, end));
        }

        long overdue = 0;
        long upcoming = 0;
        List<Map<String, Object>> items = new ArrayList<>();

        for (DueRow row : rows) {
            if (row.dueDate() == null) {
                continue;
            }
            long daysUntil = ChronoUnit.DAYS.between(today, row.dueDate());
            Bucket bucket = bucketFor(buckets, daysUntil, horizon);
            if (bucket == null) {
                continue;
            }
            bucket.add(row);
            if (daysUntil < 0) {
                overdue++;
            } else {
                upcoming++;
            }
            if (items.size() < limit) {
                items.add(item(stream, row, daysUntil, fx, showMoney));
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("key", stream.key());
        payload.put("label", stream.label());
        payload.put("resourceType", stream.resourceType());
        payload.put("valueMeaning", stream.valueMeaning());
        payload.put("overdue", overdue);
        payload.put("dueWithinHorizon", upcoming);
        payload.put("buckets", buckets.values().stream().map(b -> b.toMap(showMoney)).toList());
        payload.put("items", items);
        payload.put("truncated", overdue + upcoming > limit);
        return new Rendered(payload, overdue, upcoming);
    }

    private Bucket bucketFor(Map<String, Bucket> buckets, long daysUntil, int horizon) {
        if (daysUntil < 0) {
            return buckets.get("OVERDUE");
        }
        if (daysUntil > horizon) {
            return null;
        }
        for (Bucket b : buckets.values()) {
            if (b.contains(daysUntil)) {
                return b;
            }
        }
        return null;
    }

    private Map<String, Object> item(Stream stream, DueRow row, long daysUntil,
                                     CurrencyConversion fx, boolean showMoney) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.id());
        m.put("name", row.name());
        m.put("reference", row.reference());
        m.put("resourceType", stream.resourceType());
        m.put("dueDate", row.dueDate().toString());
        m.put("daysUntil", daysUntil);
        m.put("overdue", daysUntil < 0);
        m.put("relatedId", row.relatedId());
        m.put("relatedName", row.relatedName());
        if (showMoney) {
            // Null, not zero: an amount with no exchange rate is unknown.
            m.put("value", fx.toBase(row.effectiveAmount(), row.currency())
                    .map(CurrencyConversion::round).orElse(null));
            m.put("nativeCurrency", row.currency());
        }
        return m;
    }

    private static int bounded(Integer value, int fallback, int min, int max) {
        if (value == null) {
            return fallback;
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException("Value must be between " + min + " and " + max);
        }
        return value;
    }

    /** One time window in a stream: how many land in it and what they are worth. */
    private static final class Bucket {
        private final String key;
        private final String label;
        private final Integer fromDays;
        private final int toDays;
        private final MoneyAccumulator value;
        private long count;

        Bucket(CurrencyConversion fx, String key, String label, Integer fromDays, int toDays) {
            this.key = key;
            this.label = label;
            this.fromDays = fromDays;
            this.toDays = toDays;
            this.value = fx.newAccumulator();
        }

        boolean contains(long daysUntil) {
            return fromDays != null && daysUntil >= fromDays && daysUntil <= toDays;
        }

        void add(DueRow row) {
            count++;
            value.add(row.effectiveAmount(), row.currency());
        }

        Map<String, Object> toMap(boolean showMoney) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bucket", key);
            m.put("label", label);
            m.put("count", count);
            if (showMoney) {
                m.put("value", value.amount());
                m.put("valueComplete", value.isComplete());
            }
            return m;
        }

        @SuppressWarnings("unused")
        BigDecimal rawValue() {
            return value.rawSum();
        }
    }
}
