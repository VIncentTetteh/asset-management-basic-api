package com.assetiq.services.impl;

import com.assetiq.dto.PredictiveInsightDto;
import com.assetiq.enums.AssetCondition;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.InsightType;
import com.assetiq.enums.MaintenanceStatus;
import com.assetiq.enums.MaintenanceType;
import com.assetiq.models.Asset;
import com.assetiq.models.MaintenanceRecord;
import com.assetiq.models.Organisation;
import com.assetiq.models.PredictiveInsight;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.AuditItemRepository;
import com.assetiq.repositories.CheckoutRecordRepository;
import com.assetiq.repositories.MaintenanceRecordRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.PredictiveInsightRepository;
import com.assetiq.services.insights.AssetSightingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The insight rules must state the evidence they used, and must not claim a
 * confidence nobody measured or an idleness nobody observed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Predictive insights - evidence, not fabricated confidence")
class PredictiveInsightBasisTest {

    @Mock OrganisationRepository organisationRepository;
    @Mock AssetRepository assetRepo;
    @Mock MaintenanceRecordRepository maintenanceRepo;
    @Mock PredictiveInsightRepository insightRepo;
    @Mock CheckoutRecordRepository checkoutRepository;
    @Mock AuditItemRepository auditItemRepository;

    private PredictiveMaintenanceServiceImpl service;
    private Organisation org;

    @BeforeEach
    void setUp() {
        org = new Organisation();
        org.setId(UUID.randomUUID());
        org.setName("Tenant");
        org.setBillingCurrency("GHS");
        TenantContext.setOrganisationId(org.getId());

        lenient().when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId()))
                .thenReturn(Optional.of(org));
        lenient().when(maintenanceRepo.findByOrganisationAndDeletedAtIsNull(org)).thenReturn(Set.of());
        lenient().when(checkoutRepository.findLatestHandlingPerAsset(org)).thenReturn(List.of());
        lenient().when(auditItemRepository.findLatestVerificationPerAsset(org)).thenReturn(List.of());
        lenient().when(insightRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service = new PredictiveMaintenanceServiceImpl(organisationRepository, assetRepo,
                maintenanceRepo, insightRepo,
                new AssetSightingService(checkoutRepository, auditItemRepository));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Asset asset(String name) {
        Asset a = new Asset();
        a.setId(UUID.randomUUID());
        a.setName(name);
        a.setCurrency("GHS");
        a.setOrganisation(org);
        return a;
    }

    private List<PredictiveInsightDto> generateFor(Asset... assets) {
        when(assetRepo.findAllByOrganisationAndDeletedAtIsNull(org)).thenReturn(List.of(assets));
        return service.generateInsights();
    }

    private PredictiveInsightDto ofType(List<PredictiveInsightDto> all, InsightType type) {
        return all.stream().filter(i -> i.getInsightType() == type).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("no insight carries a confidence score any more")
    void confidenceIsGone() {
        Asset warranty = asset("Printer");
        warranty.setWarrantyExpiryDate(LocalDate.now().plusDays(30));

        Asset aging = asset("Server");
        aging.setPurchaseDate(LocalDate.now().minusMonths(30));
        aging.setUsefulLifeMonths(30);

        List<PredictiveInsightDto> insights = generateFor(warranty, aging);

        assertThat(insights).isNotEmpty();
        assertThat(insights).allSatisfy(i -> {
            assertThat(i.getConfidence()).isNull();
            assertThat(i.getBasis()).isNotBlank();
        });
    }

    @Test
    @DisplayName("a deterministic rule states the date it read, not a probability")
    void deterministicRulesCiteTheirDate() {
        LocalDate expiry = LocalDate.now().plusDays(30);
        Asset a = asset("Printer");
        a.setWarrantyExpiryDate(expiry);

        PredictiveInsightDto insight = ofType(generateFor(a), InsightType.WARRANTY_EXPIRY);

        assertThat(insight.getBasis()).isEqualTo("Warranty expiry date " + expiry);
        assertThat(insight.getConfidence()).isNull();
    }

    @Test
    @DisplayName("a heuristic states what it counted, so the user can judge the rule")
    void heuristicRulesCiteTheirEvidence() {
        Asset a = asset("Laptop");
        a.setCondition(AssetCondition.POOR);
        Set<MaintenanceRecord> records = Set.of(
                repair(a, 10), repair(a, 30), repair(a, 60));
        when(maintenanceRepo.findByOrganisationAndDeletedAtIsNull(org)).thenReturn(records);

        PredictiveInsightDto insight = ofType(generateFor(a), InsightType.FAILURE_RISK);

        assertThat(insight.getBasis()).isEqualTo("3 maintenance events in the last 90 days; condition POOR");
        assertThat(insight.getTitle()).isEqualTo("Repeated repairs in the last 90 days");
        // The description is explicit that this is a pattern in the history, not
        // a measurement of the hardware.
        assertThat(insight.getDescription()).contains("records no telemetry");
    }

    @Test
    @DisplayName("an asset last scanned long ago is reported as not seen, naming the source")
    void unseenIsMeasuredFromARealSighting() {
        Asset a = asset("Spare laptop");
        a.setStatus(AssetStatus.IN_STOCK);
        a.setPurchaseCost(new BigDecimal("2000"));
        a.setLastScannedAt(Instant.now().minus(300, ChronoUnit.DAYS));
        a.setUpdatedAt(Instant.now());   // edited today: must not make it look "in use"

        PredictiveInsightDto insight = ofType(generateFor(a), InsightType.UNDERUTILIZED);

        assertThat(insight.getTitle()).isEqualTo("Not seen for 300 days");
        assertThat(insight.getBasis()).isEqualTo("Last scanned 300 days ago");
        assertThat(insight.getDescription()).contains("last scanned 300 days ago");
    }

    @Test
    @DisplayName("a recent sighting clears the flag even when the record is stale")
    void arecentSightingClearsIt() {
        Asset a = asset("Spare laptop");
        a.setStatus(AssetStatus.IN_STOCK);
        a.setPurchaseCost(new BigDecimal("2000"));
        a.setLastScannedAt(Instant.now().minus(3, ChronoUnit.DAYS));
        // Nobody has edited the record for two years — irrelevant, it was scanned
        // on Tuesday. Under the old rule this was flagged as idle for 730 days.
        a.setUpdatedAt(Instant.now().minus(730, ChronoUnit.DAYS));

        assertThat(generateFor(a)).noneMatch(i -> i.getInsightType() == InsightType.UNDERUTILIZED);
    }

    @Test
    @DisplayName("an asset with no sighting at all says so, and says what it does know")
    void neverSeenIsItsOwnMessage() {
        Asset a = asset("Mystery box");
        a.setStatus(AssetStatus.IN_STOCK);
        a.setPurchaseCost(new BigDecimal("5000"));
        a.setUpdatedAt(Instant.now().minus(400, ChronoUnit.DAYS));

        PredictiveInsightDto insight = ofType(generateFor(a), InsightType.UNDERUTILIZED);

        assertThat(insight.getTitle()).isEqualTo("No recorded sighting");
        assertThat(insight.getBasis())
                .isEqualTo("No scan, checkout or audit on record; record last changed 400 days ago");
        assertThat(insight.getDescription()).contains("never been scanned, checked out or confirmed");
    }

    @Test
    @DisplayName("freshly imported stock is not accused of going missing")
    void newlyImportedStockIsLeftAlone() {
        Asset a = asset("Just imported");
        a.setStatus(AssetStatus.IN_STOCK);
        a.setPurchaseCost(new BigDecimal("5000"));
        a.setUpdatedAt(Instant.now().minus(2, ChronoUnit.DAYS));

        assertThat(generateFor(a)).noneMatch(i -> i.getInsightType() == InsightType.UNDERUTILIZED);
    }

    @Test
    @DisplayName("a checkout counts as a sighting, so a loaned asset is not chased")
    void aCheckoutIsASighting() {
        Asset a = asset("Loaned projector");
        a.setStatus(AssetStatus.IN_STOCK);
        a.setPurchaseCost(new BigDecimal("2000"));
        a.setUpdatedAt(Instant.now().minus(400, ChronoUnit.DAYS));
        when(checkoutRepository.findLatestHandlingPerAsset(org)).thenReturn(
                List.<Object[]>of(new Object[]{a.getId(), Instant.now().minus(10, ChronoUnit.DAYS), null}));

        assertThat(generateFor(a)).noneMatch(i -> i.getInsightType() == InsightType.UNDERUTILIZED);
    }

    @Test
    @DisplayName("the persisted insight carries the basis, not a confidence")
    void basisIsPersisted() {
        Asset a = asset("Printer");
        a.setWarrantyExpiryDate(LocalDate.now().plusDays(10));
        when(assetRepo.findAllByOrganisationAndDeletedAtIsNull(org)).thenReturn(List.of(a));

        service.generateInsights();

        // NB: the field `org` shadows the package name here, so Mockito is imported.
        ArgumentCaptor<PredictiveInsight> saved = ArgumentCaptor.forClass(PredictiveInsight.class);
        verify(insightRepo, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(i -> {
            assertThat(i.getBasis()).isNotBlank();
            assertThat(i.getConfidence()).isNull();
        });
    }

    private MaintenanceRecord repair(Asset asset, int daysAgo) {
        MaintenanceRecord r = new MaintenanceRecord();
        r.setId(UUID.randomUUID());
        r.setAsset(asset);
        r.setMaintenanceType(MaintenanceType.values()[0]);
        r.setStatus(MaintenanceStatus.COMPLETED);
        r.setPerformedDate(LocalDate.now().minusDays(daysAgo));
        r.setOrganisation(org);
        return r;
    }
}
