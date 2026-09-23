package com.assetiq.services.insights;

import com.assetiq.enums.AssetStatus;
import com.assetiq.models.AnalyticsSnapshot;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.AnalyticsSnapshotRepository;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.MaintenanceRecordRepository;
import com.assetiq.repositories.SoftwareLicenseRepository;
import com.assetiq.services.finance.DepreciationCalculator;
import com.assetiq.services.money.CurrencyConversion;
import com.assetiq.services.money.MoneyAccumulator;
import com.assetiq.services.money.MoneyAggregator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;

/**
 * Writes and reads the daily estate snapshot.
 *
 * <p>Takes the same measurements the dashboards show, so a trend and a headline
 * figure for the same day agree. Idempotent per (tenant, day): a second run on
 * the same date does nothing rather than doubling a series.
 */
@Service
public class AnalyticsSnapshotService {

    /** Matches {@link CostWasteService#DEFAULT_IDLE_DAYS} so "not seen" means one thing. */
    private static final int UNSEEN_DAYS = CostWasteService.DEFAULT_IDLE_DAYS;

    private final AssetRepository assetRepository;
    private final MaintenanceRecordRepository maintenanceRepository;
    private final SoftwareLicenseRepository licenseRepository;
    private final AnalyticsSnapshotRepository snapshotRepository;
    private final AssetSightingService sightingService;
    private final MoneyAggregator moneyAggregator;

    public AnalyticsSnapshotService(AssetRepository assetRepository,
                                    MaintenanceRecordRepository maintenanceRepository,
                                    SoftwareLicenseRepository licenseRepository,
                                    AnalyticsSnapshotRepository snapshotRepository,
                                    AssetSightingService sightingService,
                                    MoneyAggregator moneyAggregator) {
        this.assetRepository = assetRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.licenseRepository = licenseRepository;
        this.snapshotRepository = snapshotRepository;
        this.sightingService = sightingService;
        this.moneyAggregator = moneyAggregator;
    }

    /**
     * Record today's figures for one tenant.
     *
     * <p>Runs in its own transaction so one tenant's failure cannot roll back the
     * snapshots already written for the tenants before it in the same nightly run.
     *
     * @return the snapshot written, or empty when one already exists for that day
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AnalyticsSnapshot> capture(Organisation org, LocalDate on) {
        if (snapshotRepository.existsByOrganisationAndSnapshotDate(org, on)) {
            return Optional.empty();
        }

        CurrencyConversion fx = moneyAggregator.begin(org, on);
        MoneyAccumulator cost = fx.newAccumulator();
        MoneyAccumulator netBookValue = fx.newAccumulator();
        MoneyAccumulator accumulated = fx.newAccumulator();
        MoneyAccumulator monthly = fx.newAccumulator();

        Map<UUID, AssetSighting> sightings = sightingService.sightingsFor(org);
        Instant now = Instant.now();
        Instant unseenCutoff = now.minus(Duration.ofDays(UNSEEN_DAYS));
        long assetCount = 0;
        long active = 0;
        long notSeen = 0;
        long unassigned = 0;
        long fullyDepreciated = 0;

        for (AssetValuationRow row : assetRepository.findValuationRows(org)) {
            if (!row.onBooks()) {
                continue;
            }
            assetCount++;
            DepreciationCalculator.Result d = row.depreciation(on);
            cost.add(row.purchaseCost(), row.currency());
            netBookValue.add(d.netBookValue(), row.currency());
            accumulated.add(d.accumulatedDepreciation(), row.currency());
            monthly.add(d.monthlyDepreciation(), row.currency());

            if (row.active()) {
                active++;
            }
            if (row.status() == AssetStatus.IN_STOCK || row.status() == AssetStatus.RESERVED) {
                AssetSighting sighting = AssetSightingService.sightingFor(
                        row.id(), row.lastScannedAt(), sightings);
                Instant lastEvidence = sighting != null ? sighting.at() : row.lastRecordActivityAt();
                if (lastEvidence != null && lastEvidence.isBefore(unseenCutoff)) {
                    notSeen++;
                }
            }
            if (row.status() == AssetStatus.IN_USE && row.assignedUserId() == null) {
                unassigned++;
            }
            if (d.configured() && d.fullyDepreciated() && row.purchaseCost() != null) {
                fullyDepreciated++;
            }
        }

        AnalyticsSnapshot snapshot = new AnalyticsSnapshot();
        snapshot.setOrganisation(org);
        snapshot.setSnapshotDate(on);
        snapshot.setCurrency(fx.baseCurrency());
        snapshot.setComplete(fx.isComplete());
        snapshot.setAssetCount(assetCount);
        snapshot.setActiveAssetCount(active);
        snapshot.setNotSeenAssetCount(notSeen);
        snapshot.setUnassignedInUseCount(unassigned);
        snapshot.setFullyDepreciatedCount(fullyDepreciated);
        snapshot.setTotalCost(cost.amount());
        snapshot.setNetBookValue(netBookValue.amount());
        snapshot.setAccumulatedDepreciation(accumulated.amount());
        snapshot.setMonthlyDepreciation(monthly.amount());
        snapshot.setOverdueMaintenanceCount(maintenanceRepository.countOverdue(org, on));

        List<Object[]> seats = licenseRepository.sumSeats(org);
        if (!seats.isEmpty() && seats.get(0) != null) {
            snapshot.setLicenceSeatsTotal(asLong(seats.get(0)[0]));
            snapshot.setLicenceSeatsUsed(asLong(seats.get(0)[1]));
        }

        return Optional.of(snapshotRepository.save(snapshot));
    }

    private static long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
