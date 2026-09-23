package com.assetiq.services.impl;

import com.assetiq.dto.PredictiveInsightDto;
import com.assetiq.enums.AssetCondition;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.InsightSeverity;
import com.assetiq.enums.InsightType;
import com.assetiq.models.Asset;
import com.assetiq.models.MaintenanceRecord;
import com.assetiq.models.Organisation;
import com.assetiq.models.PredictiveInsight;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.MaintenanceRecordRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.PredictiveInsightRepository;
import com.assetiq.services.insights.AssetSighting;
import com.assetiq.services.insights.AssetSightingService;
import com.assetiq.services.finance.DepreciationCalculator;
import com.assetiq.services.PredictiveMaintenanceService;
import com.assetiq.services.TenantAwareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class PredictiveMaintenanceServiceImpl extends TenantAwareService implements PredictiveMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(PredictiveMaintenanceServiceImpl.class);

    /** Assets below this purchase cost are not worth chasing across a warehouse. */
    static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("1000");

    /**
     * A gap in sightings long enough to be a decision rather than an oversight.
     * Matches {@code CostWasteService.DEFAULT_IDLE_DAYS} so "not seen" means one
     * thing across the product.
     */
    static final int UNSEEN_DAYS = com.assetiq.services.insights.CostWasteService.DEFAULT_IDLE_DAYS;

    private final AssetRepository assetRepo;
    private final MaintenanceRecordRepository maintenanceRepo;
    private final PredictiveInsightRepository insightRepo;
    private final AssetSightingService sightingService;

    public PredictiveMaintenanceServiceImpl(OrganisationRepository organisationRepository,
                                            AssetRepository assetRepo,
                                            MaintenanceRecordRepository maintenanceRepo,
                                            PredictiveInsightRepository insightRepo,
                                            AssetSightingService sightingService) {
        super(organisationRepository);
        this.assetRepo = assetRepo;
        this.maintenanceRepo = maintenanceRepo;
        this.insightRepo = insightRepo;
        this.sightingService = sightingService;
    }

    // ── Generate ──────────────────────────────────────────────────────────────

    @Override
    public List<PredictiveInsightDto> generateInsights() {
        Organisation org = requireTenantOrg();
        List<PredictiveInsight> generated = analyse(org, Integer.MAX_VALUE);
        log.info("[AI] Generated {} insights for org {}", generated.size(), org.getId());
        return generated.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public int refreshInsights(int maxAssets) {
        Organisation org = requireTenantOrg();
        return analyse(org, maxAssets).size();
    }

    /**
     * Runs every rule over up to {@code maxAssets} of the tenant's assets.
     *
     * <p>Maintenance is fetched once for the whole organisation and grouped by
     * asset rather than queried per asset. The per-asset query was an N+1 that
     * cost one round trip per asset on every run — tolerable behind a button
     * pressed by hand, not on a nightly job over every tenant.
     *
     * <p>When a tenant holds more assets than the bound, the least recently
     * updated are analysed first, so a large tenant still makes progress each
     * night instead of always re-analysing the same head of the list.
     */
    private List<PredictiveInsight> analyse(Organisation org, int maxAssets) {
        List<Asset> assets = assetRepo.findAllByOrganisationAndDeletedAtIsNull(org);
        if (assets.size() > maxAssets) {
            assets = assets.stream()
                    .sorted(Comparator.comparing(Asset::getUpdatedAt,
                            Comparator.nullsFirst(Comparator.naturalOrder())))
                    .limit(maxAssets)
                    .collect(Collectors.toList());
        }

        Map<UUID, Set<MaintenanceRecord>> maintenanceByAsset =
                maintenanceRepo.findByOrganisationAndDeletedAtIsNull(org).stream()
                        .filter(r -> r.getAsset() != null)
                        .collect(Collectors.groupingBy(r -> r.getAsset().getId(), Collectors.toSet()));

        // Two grouped queries for the whole tenant, not one per asset.
        Map<UUID, AssetSighting> sightings = sightingService.sightingsFor(org);

        List<PredictiveInsight> generated = new ArrayList<>();
        for (Asset asset : assets) {
            Set<MaintenanceRecord> records =
                    maintenanceByAsset.getOrDefault(asset.getId(), Collections.emptySet());
            generated.addAll(checkMaintenanceDue(asset, records, org));
            generated.addAll(checkFailureRisk(asset, records, org));
            generated.addAll(checkWarrantyExpiry(asset, org));
            generated.addAll(checkAssetAging(asset, org));
            generated.addAll(checkDepreciationComplete(asset, org));
            generated.addAll(checkUnseen(asset, sightings, org));
        }
        return generated;
    }

    // ── Rule 1: Maintenance Due ───────────────────────────────────────────────

    private List<PredictiveInsight> checkMaintenanceDue(Asset asset, Set<MaintenanceRecord> records, Organisation org) {
        LocalDate today = LocalDate.now();
        Optional<LocalDate> latestDue = records.stream()
                .filter(r -> r.getNextDueDate() != null)
                .map(MaintenanceRecord::getNextDueDate)
                .max(Comparator.naturalOrder());

        if (latestDue.isEmpty()) return Collections.emptyList();

        LocalDate dueDate = latestDue.get();
        long daysUntilDue = ChronoUnit.DAYS.between(today, dueDate);
        if (daysUntilDue > 30) return Collections.emptyList();

        InsightSeverity severity;
        String title;
        if (daysUntilDue < 0) {
            severity = InsightSeverity.CRITICAL;
            title = "Maintenance overdue by " + Math.abs(daysUntilDue) + " days";
        } else if (daysUntilDue <= 7) {
            severity = InsightSeverity.HIGH;
            title = "Maintenance due in " + daysUntilDue + " days";
        } else {
            severity = InsightSeverity.MEDIUM;
            title = "Maintenance due in " + daysUntilDue + " days";
        }

        return List.of(upsertInsight(asset, InsightType.MAINTENANCE_DUE, severity,
                title,
                "Asset '" + asset.getName() + "' has scheduled maintenance due on " + dueDate +
                        ". Ensure timely servicing to avoid downtime.",
                // Arithmetic on a stored date. Nothing here is predicted.
                "Scheduled maintenance due " + dueDate,
                dueDate, org));
    }

    // ── Rule 2: Failure Risk ──────────────────────────────────────────────────

    private List<PredictiveInsight> checkFailureRisk(Asset asset, Set<MaintenanceRecord> records, Organisation org) {
        LocalDate cutoff = LocalDate.now().minusDays(90);
        long recentCount = records.stream()
                .filter(r -> r.getPerformedDate() != null && r.getPerformedDate().isAfter(cutoff))
                .count();

        boolean badCondition = isBadCondition(asset.getCondition());

        if (recentCount < 3 && !badCondition) return Collections.emptyList();

        InsightSeverity severity = badCondition ? InsightSeverity.CRITICAL
                : (recentCount >= 5 ? InsightSeverity.HIGH : InsightSeverity.MEDIUM);

        String desc = "Asset had " + recentCount + " maintenance events in the past 90 days" +
                (badCondition ? " and is in " + asset.getCondition() + " condition" : "") +
                ". Frequent repairs often precede a failure, but AssetIQ records no " +
                "telemetry from the asset itself — this is a pattern in the maintenance " +
                "history, not a measurement of the hardware.";

        String basis = recentCount + " maintenance events in the last 90 days"
                + (badCondition ? "; condition " + asset.getCondition() : "");

        return List.of(upsertInsight(asset, InsightType.FAILURE_RISK, severity,
                "Repeated repairs in the last 90 days",
                desc, basis, LocalDate.now().plusDays(30), org));
    }

    // ── Rule 3: Warranty Expiry ───────────────────────────────────────────────

    /** POOR, DAMAGED and SCRAP all indicate failure risk (POOR is a valid condition since it was added to the enum). */
    static boolean isBadCondition(AssetCondition condition) {
        return condition == AssetCondition.POOR || condition == AssetCondition.DAMAGED
                || condition == AssetCondition.SCRAP;
    }

    private List<PredictiveInsight> checkWarrantyExpiry(Asset asset, Organisation org) {
        if (asset.getWarrantyExpiryDate() == null) return Collections.emptyList();

        long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), asset.getWarrantyExpiryDate());
        if (daysLeft > 60 || daysLeft < 0) return Collections.emptyList();

        InsightSeverity severity = daysLeft <= 14 ? InsightSeverity.HIGH : InsightSeverity.MEDIUM;

        return List.of(upsertInsight(asset, InsightType.WARRANTY_EXPIRY, severity,
                "Warranty expiring in " + daysLeft + " days",
                "The warranty for '" + asset.getName() + "' expires on " + asset.getWarrantyExpiryDate() +
                        ". Consider renewal or replacement planning.",
                // A stored date and today's date. Certain, not predicted.
                "Warranty expiry date " + asset.getWarrantyExpiryDate(),
                asset.getWarrantyExpiryDate(), org));
    }

    // ── Rule 4: Asset Aging ───────────────────────────────────────────────────

    private List<PredictiveInsight> checkAssetAging(Asset asset, Organisation org) {
        if (asset.getPurchaseDate() == null || asset.getUsefulLifeMonths() == null
                || asset.getUsefulLifeMonths() <= 0) return Collections.emptyList();

        long monthsOwned = ChronoUnit.MONTHS.between(asset.getPurchaseDate(), LocalDate.now());
        double utilizationPct = (double) monthsOwned / asset.getUsefulLifeMonths() * 100.0;
        if (utilizationPct < 80) return Collections.emptyList();

        InsightSeverity severity = utilizationPct >= 100 ? InsightSeverity.HIGH : InsightSeverity.MEDIUM;
        LocalDate endOfLife = asset.getPurchaseDate().plusMonths(asset.getUsefulLifeMonths());

        return List.of(upsertInsight(asset, InsightType.ASSET_AGING, severity,
                String.format("Asset at %.0f%% of useful life", utilizationPct),
                "'" + asset.getName() + "' has consumed " + String.format("%.0f%%", utilizationPct) +
                        " of its " + asset.getUsefulLifeMonths() + "-month useful life. " +
                        "Expected end-of-life: " + endOfLife + ". Plan replacement. " +
                        "Useful life is the accounting life on the record, not a measured " +
                        "prediction of when this asset will stop working.",
                monthsOwned + " of " + asset.getUsefulLifeMonths() + " months of useful life elapsed "
                        + "(purchased " + asset.getPurchaseDate() + ")",
                endOfLife, org));
    }

    // ── Rule 5: Depreciation Complete ────────────────────────────────────────

    private List<PredictiveInsight> checkDepreciationComplete(Asset asset, Organisation org) {
        if (!AssetStatus.IN_USE.equals(asset.getStatus())) return Collections.emptyList();
        DepreciationCalculator.Result dep = DepreciationCalculator.forAsset(asset, LocalDate.now());
        if (!dep.configured() || !dep.fullyDepreciated()) return Collections.emptyList();

        return List.of(upsertInsight(asset, InsightType.DEPRECIATION_COMPLETE, InsightSeverity.LOW,
                "Asset fully depreciated but still active",
                "'" + asset.getName() + "' has reached its residual value of " +
                        dep.residualValue() + " " + asset.getCurrency() +
                        " but remains in active use. Review disposal or write-off.",
                // The depreciation engine's own output. Arithmetic, not a forecast.
                "Fully depreciated to residual value " + dep.residualValue() + " " + asset.getCurrency()
                        + " after " + dep.monthsInService() + " months in service",
                LocalDate.now(), org));
    }

    // ── Rule 6: Not seen (high-value stock nobody has laid eyes on) ───────────

    /**
     * High-value assets held in stock that nobody has been recorded seeing for
     * {@link #UNSEEN_DAYS} days.
     *
     * <p>This used to be called "idle", measured from {@code updatedAt}, and it
     * was wrong in the way that matters: {@code updatedAt} moves when anyone
     * edits the record, so a bulk import or a corrected serial number reset it,
     * and an asset in daily use could be reported as idle for six months. It
     * cannot see use either — AssetIQ records no usage telemetry.
     *
     * <p>What it can see is a sighting: a scan, a checkout or check-in, or a
     * physical audit verification. So the rule now measures exactly that, and
     * says so. Two cases, kept apart because they mean different things:
     * an asset last seen a long time ago, and an asset nobody has ever recorded
     * seeing at all. The second is only raised once the record itself has been
     * on the books longer than the threshold, so a tenant who imported their
     * stock yesterday is not told it has gone missing.
     */
    private List<PredictiveInsight> checkUnseen(Asset asset, Map<UUID, AssetSighting> sightings,
                                                Organisation org) {
        if (!AssetStatus.IN_STOCK.equals(asset.getStatus()) && !AssetStatus.RETIRED.equals(asset.getStatus())) {
            return Collections.emptyList();
        }
        if (asset.getPurchaseCost() == null
                || asset.getPurchaseCost().compareTo(HIGH_VALUE_THRESHOLD) < 0) return Collections.emptyList();

        Instant now = Instant.now();
        AssetSighting sighting = AssetSightingService.sightingFor(
                asset.getId(), asset.getLastScannedAt(), sightings);

        String title;
        String description;
        String basis;

        if (sighting != null) {
            long days = sighting.daysAgo(now);
            if (days < UNSEEN_DAYS) return Collections.emptyList();
            title = "Not seen for " + days + " days";
            description = "'" + asset.getName() + "' (value: " + asset.getPurchaseCost() + " "
                    + asset.getCurrency() + ") was last " + sighting.source().verb() + " " + days
                    + " days ago and is held in stock. Consider redeployment or disposal to "
                    + "recover value, or scan it to confirm it is still there.";
            basis = sighting.describe(now);
        } else {
            // No scan, no checkout, no audit. All we know is how long the record
            // has existed without any of those happening - which is a lower bound
            // on the gap, and is reported as exactly that.
            Instant recordActivity = asset.getUpdatedAt() != null ? asset.getUpdatedAt() : asset.getCreatedAt();
            if (recordActivity == null) return Collections.emptyList();
            long days = ChronoUnit.DAYS.between(recordActivity, now);
            if (days < UNSEEN_DAYS) return Collections.emptyList();
            title = "No recorded sighting";
            description = "'" + asset.getName() + "' (value: " + asset.getPurchaseCost() + " "
                    + asset.getCurrency() + ") is held in stock and has never been scanned, "
                    + "checked out or confirmed by an audit. Its record has not changed for "
                    + days + " days either, so there is no evidence anybody has seen it in at "
                    + "least that long. Scan it to confirm it is still there.";
            basis = "No scan, checkout or audit on record; record last changed " + days + " days ago";
        }

        return List.of(upsertInsight(asset, InsightType.UNDERUTILIZED, InsightSeverity.LOW,
                title, description, basis, null, org));
    }

    // ── Upsert helper ─────────────────────────────────────────────────────────

    private PredictiveInsight upsertInsight(Asset asset, InsightType type, InsightSeverity severity,
                                             String title, String description, String basis,
                                             LocalDate predictedDate, Organisation org) {
        insightRepo.deleteUnresolvedByAssetAndType(asset, type);

        PredictiveInsight insight = new PredictiveInsight();
        insight.setAsset(asset);
        insight.setInsightType(type);
        insight.setSeverity(severity);
        insight.setTitle(title);
        insight.setDescription(description);
        insight.setBasis(basis);
        insight.setPredictedDate(predictedDate);
        insight.setOrganisation(org);
        return insightRepo.save(insight);
    }

    // ── Query operations ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<PredictiveInsightDto> getInsights(String type, String severity, boolean unresolvedOnly) {
        Organisation org = requireTenantOrg();
        List<PredictiveInsight> insights;

        if (unresolvedOnly) {
            insights = insightRepo.findByOrganisationAndResolvedFalseAndDeletedAtIsNullOrderByCreatedAtDesc(org);
        } else {
            insights = insightRepo.findByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(org);
        }

        if (type != null && !type.isBlank()) {
            InsightType it = InsightType.valueOf(type.toUpperCase());
            insights = insights.stream().filter(i -> it.equals(i.getInsightType())).collect(Collectors.toList());
        }
        if (severity != null && !severity.isBlank()) {
            InsightSeverity sv = InsightSeverity.valueOf(severity.toUpperCase());
            insights = insights.stream().filter(i -> sv.equals(i.getSeverity())).collect(Collectors.toList());
        }

        return insights.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PredictiveInsightDto getById(UUID id) {
        Organisation org = requireTenantOrg();
        PredictiveInsight insight = insightRepo.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new NoSuchElementException("Insight not found: " + id));
        return toDto(insight);
    }

    @Override
    public void resolve(UUID id) {
        Organisation org = requireTenantOrg();
        PredictiveInsight insight = insightRepo.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new NoSuchElementException("Insight not found: " + id));
        insight.setResolved(true);
        insight.setResolvedAt(Instant.now());
        insightRepo.save(insight);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getSummary() {
        Organisation org = requireTenantOrg();
        long total    = insightRepo.countByOrganisationAndResolvedFalseAndDeletedAtIsNull(org);
        long critical = insightRepo.countByOrganisationAndSeverityAndResolvedFalseAndDeletedAtIsNull(org, InsightSeverity.CRITICAL);
        long high     = insightRepo.countByOrganisationAndSeverityAndResolvedFalseAndDeletedAtIsNull(org, InsightSeverity.HIGH);
        long medium   = insightRepo.countByOrganisationAndSeverityAndResolvedFalseAndDeletedAtIsNull(org, InsightSeverity.MEDIUM);
        long low      = insightRepo.countByOrganisationAndSeverityAndResolvedFalseAndDeletedAtIsNull(org, InsightSeverity.LOW);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalUnresolved", total);
        summary.put("bySeverity", Map.of(
                "CRITICAL", critical, "HIGH", high, "MEDIUM", medium, "LOW", low));
        return summary;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private PredictiveInsightDto toDto(PredictiveInsight i) {
        PredictiveInsightDto dto = new PredictiveInsightDto();
        dto.setId(i.getId());
        dto.setAssetId(i.getAsset().getId());
        dto.setAssetName(i.getAsset().getName());
        dto.setAssetTag(i.getAsset().getAssetTag());
        dto.setInsightType(i.getInsightType());
        dto.setSeverity(i.getSeverity());
        dto.setTitle(i.getTitle());
        dto.setDescription(i.getDescription());
        dto.setBasis(i.getBasis());
        dto.setConfidence(i.getConfidence());   // always null since V64; see the DTO
        dto.setPredictedDate(i.getPredictedDate());
        dto.setResolved(i.isResolved());
        dto.setResolvedAt(i.getResolvedAt());
        dto.setCreatedAt(i.getCreatedAt());
        return dto;
    }
}
