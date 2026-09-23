package com.assetiq.services.insights;

import com.assetiq.enums.AssetStatus;
import com.assetiq.models.AnalyticsSnapshot;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.AnalyticsSnapshotRepository;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.AuditItemRepository;
import com.assetiq.repositories.CheckoutRecordRepository;
import com.assetiq.repositories.MaintenanceRecordRepository;
import com.assetiq.repositories.SoftwareLicenseRepository;
import com.assetiq.services.money.MoneyTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsSnapshotService")
class AnalyticsSnapshotServiceTest {

    @Mock AssetRepository assetRepository;
    @Mock MaintenanceRecordRepository maintenanceRepository;
    @Mock SoftwareLicenseRepository licenseRepository;
    @Mock AnalyticsSnapshotRepository snapshotRepository;
    @Mock CheckoutRecordRepository checkoutRepository;
    @Mock AuditItemRepository auditItemRepository;

    private AnalyticsSnapshotService service;
    private Organisation org;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        lenient().when(checkoutRepository.findLatestHandlingPerAsset(any())).thenReturn(List.of());
        lenient().when(auditItemRepository.findLatestVerificationPerAsset(any())).thenReturn(List.of());
        service = new AnalyticsSnapshotService(assetRepository, maintenanceRepository,
                licenseRepository, snapshotRepository,
                new AssetSightingService(checkoutRepository, auditItemRepository),
                MoneyTestSupport.aggregatorWithRates(Map.of("USD", "15")));
        org = InsightTestFixtures.org("GHS");
        lenient().when(snapshotRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    /** The repository returns rows of [totalSeats, usedSeats]. */
    private static List<Object[]> seats(long total, long used) {
        return List.<Object[]>of(new Object[]{total, used});
    }

    @Test
    @DisplayName("a snapshot measures the same things the dashboards show")
    void snapshotMatchesTheDashboardFigures() {
        when(snapshotRepository.existsByOrganisationAndSnapshotDate(org, today)).thenReturn(false);
        when(assetRepository.findValuationRows(org)).thenReturn(List.of(
                InsightTestFixtures.asset("A", "GHS", "1000")
                        .depreciatedOver(10, today.minusMonths(12)).build(),          // fully depreciated
                InsightTestFixtures.asset("B", "USD", "100")
                        .depreciatedOver(10, today.minusMonths(2)).build(),           // 20 USD written off
                InsightTestFixtures.asset("C", "GHS", "200")
                        .status(AssetStatus.IN_STOCK).scannedDaysAgo(400)
                        .touchedDaysAgo(400).build(),                                 // not seen for a year
                InsightTestFixtures.asset("D", "GHS", "300").unassigned().build(),    // in use, nobody holds it
                InsightTestFixtures.asset("E", "GHS", "999")
                        .status(AssetStatus.DISPOSED).build()));                      // off the books
        when(maintenanceRepository.countOverdue(org, today)).thenReturn(3L);
        when(licenseRepository.sumSeats(org)).thenReturn(seats(50L, 31L));

        AnalyticsSnapshot snapshot = service.capture(org, today).orElseThrow();

        assertThat(snapshot.getAssetCount()).isEqualTo(4L);
        assertThat(snapshot.getActiveAssetCount()).isEqualTo(4L);
        assertThat(snapshot.getNotSeenAssetCount()).isEqualTo(1L);
        assertThat(snapshot.getUnassignedInUseCount()).isEqualTo(1L);
        assertThat(snapshot.getFullyDepreciatedCount()).isEqualTo(1L);
        assertThat(snapshot.getTotalCost().toPlainString()).isEqualTo("3000.00");   // 1000 + 1500 + 200 + 300
        assertThat(snapshot.getNetBookValue().toPlainString()).isEqualTo("1700.00"); // 0 + 1200 + 200 + 300
        assertThat(snapshot.getAccumulatedDepreciation().toPlainString()).isEqualTo("1300.00");
        assertThat(snapshot.getOverdueMaintenanceCount()).isEqualTo(3L);
        assertThat(snapshot.getLicenceSeatsTotal()).isEqualTo(50L);
        assertThat(snapshot.getLicenceSeatsUsed()).isEqualTo(31L);
        assertThat(snapshot.getCurrency()).isEqualTo("GHS");
        assertThat(snapshot.isComplete()).isTrue();
    }

    @Test
    @DisplayName("an incomplete conversion is recorded, not hidden")
    void incompleteConversionIsRecorded() {
        when(snapshotRepository.existsByOrganisationAndSnapshotDate(org, today)).thenReturn(false);
        when(assetRepository.findValuationRows(org)).thenReturn(List.of(
                InsightTestFixtures.asset("Unrateable", "JPY", "9999").build()));
        when(maintenanceRepository.countOverdue(eq(org), any())).thenReturn(0L);
        when(licenseRepository.sumSeats(org)).thenReturn(seats(0L, 0L));

        AnalyticsSnapshot snapshot = service.capture(org, today).orElseThrow();

        assertThat(snapshot.isComplete()).isFalse();
        assertThat(snapshot.getTotalCost().toPlainString()).isEqualTo("0.00");
        assertThat(snapshot.getAssetCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("running twice on the same day writes nothing the second time")
    void captureIsIdempotentPerDay() {
        when(snapshotRepository.existsByOrganisationAndSnapshotDate(org, today)).thenReturn(true);

        assertThat(service.capture(org, today)).isEmpty();

        verify(snapshotRepository, never()).save(any());
        verify(assetRepository, never()).findValuationRows(any());
    }

    @Test
    @DisplayName("an empty tenant is snapshotted as empty, not skipped")
    void emptyTenantIsStillRecorded() {
        when(snapshotRepository.existsByOrganisationAndSnapshotDate(org, today)).thenReturn(false);
        when(assetRepository.findValuationRows(org)).thenReturn(List.of());
        when(maintenanceRepository.countOverdue(eq(org), any())).thenReturn(0L);
        when(licenseRepository.sumSeats(org)).thenReturn(seats(0L, 0L));

        Optional<AnalyticsSnapshot> snapshot = service.capture(org, today);

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().getAssetCount()).isZero();
        assertThat(snapshot.get().getTotalCost().toPlainString()).isEqualTo("0.00");
        assertThat(snapshot.get().isComplete()).isTrue();
    }
}
