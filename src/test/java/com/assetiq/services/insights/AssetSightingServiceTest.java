package com.assetiq.services.insights;

import com.assetiq.models.Organisation;
import com.assetiq.repositories.AuditItemRepository;
import com.assetiq.repositories.CheckoutRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssetSightingService - only real observations count as sightings")
class AssetSightingServiceTest {

    @Mock CheckoutRecordRepository checkoutRepository;
    @Mock AuditItemRepository auditItemRepository;

    private AssetSightingService service;
    private Organisation org;
    private final UUID assetId = UUID.randomUUID();
    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        service = new AssetSightingService(checkoutRepository, auditItemRepository);
        org = InsightTestFixtures.org("GHS");
        lenient().when(checkoutRepository.findLatestHandlingPerAsset(org)).thenReturn(List.of());
        lenient().when(auditItemRepository.findLatestVerificationPerAsset(org)).thenReturn(List.of());
    }

    private static List<Object[]> rows(Object... row) {
        return List.<Object[]>of(row);
    }

    @Test
    @DisplayName("the most recent of scan, checkout and audit wins")
    void latestSourceWins() {
        Instant checkedOut = now.minus(60, ChronoUnit.DAYS);
        Instant audited = now.minus(20, ChronoUnit.DAYS);
        when(checkoutRepository.findLatestHandlingPerAsset(org))
                .thenReturn(rows(assetId, checkedOut, null));
        when(auditItemRepository.findLatestVerificationPerAsset(org))
                .thenReturn(rows(assetId, audited));

        Map<UUID, AssetSighting> sightings = service.sightingsFor(org);

        // Audit beats the older checkout.
        assertThat(sightings.get(assetId).source()).isEqualTo(AssetSighting.Source.AUDIT);

        // A scan on the asset row beats both when it is more recent.
        AssetSighting withScan = AssetSightingService.sightingFor(
                assetId, now.minus(2, ChronoUnit.DAYS), sightings);
        assertThat(withScan.source()).isEqualTo(AssetSighting.Source.SCAN);
        assertThat(withScan.daysAgo(now)).isEqualTo(2L);

        // ...and loses when it is older.
        AssetSighting withOldScan = AssetSightingService.sightingFor(
                assetId, now.minus(200, ChronoUnit.DAYS), sightings);
        assertThat(withOldScan.source()).isEqualTo(AssetSighting.Source.AUDIT);
    }

    @Test
    @DisplayName("a check-in is a sighting too, read as the end of the day it happened")
    void aReturnDateIsASighting() {
        LocalDate returned = LocalDate.now().minusDays(3);
        when(checkoutRepository.findLatestHandlingPerAsset(org))
                .thenReturn(rows(assetId, now.minus(90, ChronoUnit.DAYS), returned));

        AssetSighting sighting = service.sightingsFor(org).get(assetId);

        assertThat(sighting.source()).isEqualTo(AssetSighting.Source.CHECKOUT);
        // The later of hand-out and hand-back, so roughly three days rather than ninety.
        assertThat(sighting.daysAgo(now)).isLessThanOrEqualTo(3L);
    }

    @Test
    @DisplayName("an asset nobody has ever seen has no sighting - not an old one")
    void neverSeenIsNullNotAncient() {
        assertThat(service.sightingsFor(org)).isEmpty();
        assertThat(AssetSightingService.sightingFor(assetId, null, Map.of())).isNull();
    }

    @Test
    @DisplayName("editing the record is not a sighting")
    void recordActivityIsNotASighting() {
        // The row was edited today but never scanned, checked out or audited.
        AssetValuationRow row = InsightTestFixtures.asset("Laptop", "GHS", "1000")
                .touchedDaysAgo(0).build();

        assertThat(AssetSightingService.sightingFor(row.id(), row.lastScannedAt(), Map.of())).isNull();
        assertThat(row.lastRecordActivityAt()).isNotNull();
    }

    @Test
    @DisplayName("the description names the source, so a user can check it")
    void descriptionNamesTheSource() {
        AssetSighting scanned = new AssetSighting(now.minus(40, ChronoUnit.DAYS),
                AssetSighting.Source.SCAN);
        assertThat(scanned.describe(now)).isEqualTo("Last scanned 40 days ago");

        AssetSighting audited = new AssetSighting(now.minus(5, ChronoUnit.DAYS),
                AssetSighting.Source.AUDIT);
        assertThat(audited.describe(now)).isEqualTo("Last confirmed by audit 5 days ago");
    }
}
