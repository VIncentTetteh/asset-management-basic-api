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

    private final AssetRepository assetRepo;
    private final MaintenanceRecordRepository maintenanceRepo;
    private final PredictiveInsightRepository insightRepo;

    public PredictiveMaintenanceServiceImpl(OrganisationRepository organisationRepository,
                                            AssetRepository assetRepo,
                                            MaintenanceRecordRepository maintenanceRepo,
                                            PredictiveInsightRepository insightRepo) {
        super(organisationRepository);
        this.assetRepo = assetRepo;
        this.maintenanceRepo = maintenanceRepo;
        this.insightRepo = insightRepo;
    }

    // ── Generate ──────────────────────────────────────────────────────────────

    @Override
    public List<PredictiveInsightDto> generateInsights() {
        Organisation org = requireTenantOrg();
        List<Asset> assets = assetRepo.findAllByOrganisationAndDeletedAtIsNull(org);
        List<PredictiveInsight> generated = new ArrayList<>();

        for (Asset asset : assets) {
            Set<MaintenanceRecord> records = maintenanceRepo.findByAssetIdAndDeletedAtIsNull(asset.getId());
            generated.addAll(checkMaintenanceDue(asset, records, org));
            generated.addAll(checkFailureRisk(asset, records, org));
            generated.addAll(checkWarrantyExpiry(asset, org));
            generated.addAll(checkAssetAging(asset, org));
            generated.addAll(checkDepreciationComplete(asset, org));
            generated.addAll(checkUnderutilized(asset, org));
        }

        log.info("[AI] Generated {} insights for org {}", generated.size(), org.getId());
        return generated.stream().map(this::toDto).collect(Collectors.toList());
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
                0.90, dueDate, org));
    }

    // ── Rule 2: Failure Risk ──────────────────────────────────────────────────

    private List<PredictiveInsight> checkFailureRisk(Asset asset, Set<MaintenanceRecord> records, Organisation org) {
        LocalDate cutoff = LocalDate.now().minusDays(90);
        long recentCount = records.stream()
                .filter(r -> r.getPerformedDate() != null && r.getPerformedDate().isAfter(cutoff))
                .count();

        // DAMAGED or SCRAP maps to the old "POOR/CRITICAL" concept
        boolean badCondition = AssetCondition.DAMAGED.equals(asset.getCondition())
                || AssetCondition.SCRAP.equals(asset.getCondition());

        if (recentCount < 3 && !badCondition) return Collections.emptyList();

        InsightSeverity severity = badCondition ? InsightSeverity.CRITICAL
                : (recentCount >= 5 ? InsightSeverity.HIGH : InsightSeverity.MEDIUM);

        double confidence = Math.min(0.95, 0.60 + (recentCount * 0.05) + (badCondition ? 0.20 : 0));
        String desc = "Asset had " + recentCount + " maintenance events in the past 90 days" +
                (badCondition ? " and is in " + asset.getCondition() + " condition" : "") +
                ". High frequency indicates potential hardware failure.";

        return List.of(upsertInsight(asset, InsightType.FAILURE_RISK, severity,
                "Elevated failure risk detected",
                desc, confidence, LocalDate.now().plusDays(30), org));
    }

    // ── Rule 3: Warranty Expiry ───────────────────────────────────────────────

    private List<PredictiveInsight> checkWarrantyExpiry(Asset asset, Organisation org) {
        if (asset.getWarrantyExpiryDate() == null) return Collections.emptyList();

        long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), asset.getWarrantyExpiryDate());
        if (daysLeft > 60 || daysLeft < 0) return Collections.emptyList();

        InsightSeverity severity = daysLeft <= 14 ? InsightSeverity.HIGH : InsightSeverity.MEDIUM;

        return List.of(upsertInsight(asset, InsightType.WARRANTY_EXPIRY, severity,
                "Warranty expiring in " + daysLeft + " days",
                "The warranty for '" + asset.getName() + "' expires on " + asset.getWarrantyExpiryDate() +
                        ". Consider renewal or replacement planning.",
                0.99, asset.getWarrantyExpiryDate(), org));
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
                        "Expected end-of-life: " + endOfLife + ". Plan replacement.",
                0.85, endOfLife, org));
    }

    // ── Rule 5: Depreciation Complete ────────────────────────────────────────

    private List<PredictiveInsight> checkDepreciationComplete(Asset asset, Organisation org) {
        if (asset.getCurrentBookValue() == null || asset.getResidualValue() == null) return Collections.emptyList();
        if (asset.getCurrentBookValue().compareTo(asset.getResidualValue()) > 0) return Collections.emptyList();
        if (!AssetStatus.IN_USE.equals(asset.getStatus())) return Collections.emptyList();

        return List.of(upsertInsight(asset, InsightType.DEPRECIATION_COMPLETE, InsightSeverity.LOW,
                "Asset fully depreciated but still active",
                "'" + asset.getName() + "' has reached its residual value of " +
                        asset.getResidualValue() + " " + asset.getCurrency() +
                        " but remains in active use. Review disposal or write-off.",
                0.99, LocalDate.now(), org));
    }

    // ── Rule 6: Underutilized (IN_STOCK for >180 days with high value) ────────

    private List<PredictiveInsight> checkUnderutilized(Asset asset, Organisation org) {
        if (!AssetStatus.IN_STOCK.equals(asset.getStatus()) && !AssetStatus.RETIRED.equals(asset.getStatus())) {
            return Collections.emptyList();
        }
        if (asset.getPurchaseCost() == null
                || asset.getPurchaseCost().compareTo(new BigDecimal("1000")) < 0) return Collections.emptyList();
        if (asset.getUpdatedAt() == null) return Collections.emptyList();

        long daysIdle = ChronoUnit.DAYS.between(
                asset.getUpdatedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate(), LocalDate.now());
        if (daysIdle < 180) return Collections.emptyList();

        return List.of(upsertInsight(asset, InsightType.UNDERUTILIZED, InsightSeverity.LOW,
                "High-value asset idle for " + daysIdle + " days",
                "'" + asset.getName() + "' (value: " + asset.getPurchaseCost() + " " + asset.getCurrency() +
                        ") has been idle for " + daysIdle + " days. " +
                        "Consider redeployment or disposal to recover value.",
                0.80, null, org));
    }

    // ── Upsert helper ─────────────────────────────────────────────────────────

    private PredictiveInsight upsertInsight(Asset asset, InsightType type, InsightSeverity severity,
                                             String title, String description, double confidence,
                                             LocalDate predictedDate, Organisation org) {
        insightRepo.deleteUnresolvedByAssetAndType(asset, type);

        PredictiveInsight insight = new PredictiveInsight();
        insight.setAsset(asset);
        insight.setInsightType(type);
        insight.setSeverity(severity);
        insight.setTitle(title);
        insight.setDescription(description);
        insight.setConfidence(confidence);
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
        dto.setConfidence(i.getConfidence());
        dto.setPredictedDate(i.getPredictedDate());
        dto.setResolved(i.isResolved());
        dto.setResolvedAt(i.getResolvedAt());
        dto.setCreatedAt(i.getCreatedAt());
        return dto;
    }
}
