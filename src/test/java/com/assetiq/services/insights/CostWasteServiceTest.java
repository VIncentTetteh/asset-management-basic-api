package com.assetiq.services.insights;

import com.assetiq.enums.AssetCondition;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.LicenseStatus;
import com.assetiq.enums.LicenseType;
import com.assetiq.models.Organisation;
import com.assetiq.models.SoftwareLicense;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.AuditItemRepository;
import com.assetiq.repositories.CheckoutRecordRepository;
import com.assetiq.repositories.SoftwareLicenseRepository;
import com.assetiq.services.money.MoneyTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CostWasteService")
class CostWasteServiceTest {

    @Mock AssetRepository assetRepository;
    @Mock SoftwareLicenseRepository licenseRepository;
    @Mock CheckoutRecordRepository checkoutRepository;
    @Mock AuditItemRepository auditItemRepository;

    private CostWasteService service;
    private Organisation org;
    private final Set<InsightSection> everything = EnumSet.allOf(InsightSection.class);

    @BeforeEach
    void setUp() {
        // No checkouts and no audits by default: the only sightings a test sees
        // are the ones it sets up on the asset row itself.
        lenient().when(checkoutRepository.findLatestHandlingPerAsset(any())).thenReturn(List.of());
        lenient().when(auditItemRepository.findLatestVerificationPerAsset(any())).thenReturn(List.of());
        service = new CostWasteService(assetRepository, licenseRepository,
                new AssetSightingService(checkoutRepository, auditItemRepository),
                MoneyTestSupport.aggregatorWithRates(Map.of("USD", "10")));
        org = InsightTestFixtures.org("GHS");
    }

    private Map<String, Object> finding(Map<String, Object> response, String key) {
        return findings(response).stream()
                .filter(f -> key.equals(f.get("key"))).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("each rule counts exactly the assets it describes")
    void rulesCountWhatTheySay() {
        LocalDate today = LocalDate.now();
        when(assetRepository.findValuationRows(org)).thenReturn(List.of(
                // Fully depreciated 2 months ago and still in service.
                InsightTestFixtures.asset("Laptop", "GHS", "1000")
                        .depreciatedOver(12, today.minusMonths(14)).build(),
                // In stock, last scanned a year ago: a real gap in sightings.
                InsightTestFixtures.asset("Spare", "GHS", "500")
                        .status(AssetStatus.IN_STOCK).scannedDaysAgo(365).touchedDaysAgo(365).build(),
                // In stock and scanned last week: seen recently, not flagged.
                InsightTestFixtures.asset("Fresh", "GHS", "500")
                        .status(AssetStatus.IN_STOCK).scannedDaysAgo(7).touchedDaysAgo(7).build(),
                // In use with nobody holding it.
                InsightTestFixtures.asset("Orphan", "GHS", "300").unassigned().build(),
                InsightTestFixtures.asset("Gone", "GHS", "200").status(AssetStatus.MISSING).build(),
                InsightTestFixtures.asset("Broken", "GHS", "100")
                        .condition(AssetCondition.SCRAP).build(),
                // Disposed: off the books, not anyone's problem any more.
                InsightTestFixtures.asset("Sold", "GHS", "900")
                        .status(AssetStatus.DISPOSED).condition(AssetCondition.SCRAP).build()));
        when(licenseRepository.findByOrganisationAndDeletedAtIsNull(org)).thenReturn(List.of());

        Map<String, Object> r = service.costWaste(org, null, null, everything);

        assertThat(finding(r, "FULLY_DEPRECIATED_ACTIVE").get("count")).isEqualTo(1L);
        assertThat(finding(r, "NOT_SEEN_IN_STOCK").get("count")).isEqualTo(1L);
        assertThat(finding(r, "UNASSIGNED_IN_USE").get("count")).isEqualTo(1L);
        assertThat(finding(r, "MISSING").get("count")).isEqualTo(1L);
        assertThat(finding(r, "UNUSABLE_CONDITION").get("count")).isEqualTo(1L);
    }

    @Test
    @DisplayName("an asset caught by two rules is counted once in the de-duplicated totals")
    void overlappingFindingsAreNotDoubleCounted() {
        // Missing AND scrap AND unassigned-in-use is impossible in one asset, but
        // missing AND scrap is not, and both rules will claim it.
        when(assetRepository.findValuationRows(org)).thenReturn(List.of(
                InsightTestFixtures.asset("Wreck", "GHS", "400")
                        .status(AssetStatus.MISSING).condition(AssetCondition.DAMAGED).build()));
        when(licenseRepository.findByOrganisationAndDeletedAtIsNull(org)).thenReturn(List.of());

        Map<String, Object> r = service.costWaste(org, null, null, everything);

        assertThat(finding(r, "MISSING").get("count")).isEqualTo(1L);
        assertThat(finding(r, "UNUSABLE_CONDITION").get("count")).isEqualTo(1L);
        assertThat(r.get("distinctAssetsFlagged")).isEqualTo(1L);
        // Its book value appears once, not twice.
        assertThat(((BigDecimal) r.get("capitalTiedUp")).toPlainString()).isEqualTo("400.00");
    }

    @Test
    @DisplayName("unused licence seats are priced per seat and converted")
    void unusedSeatsArePricedPerSeat() {
        when(assetRepository.findValuationRows(org)).thenReturn(List.of());
        when(licenseRepository.findByOrganisationAndDeletedAtIsNull(org)).thenReturn(List.of(
                licence("IDE", "USD", "1200", 100, 60),      // 40 spare x 12 USD = 480 USD = 4800 GHS
                licence("CRM", "GHS", "1000", 10, 10),       // fully used: not a finding
                licence("Chat", "GHS", "500", 5, 8)));       // 3 seats over

        Map<String, Object> r = service.costWaste(org, null, null, everything);

        Map<String, Object> unused = finding(r, "LICENCE_UNUSED_SEATS");
        assertThat(unused.get("count")).isEqualTo(1L);
        assertThat(unused.get("unusedSeats")).isEqualTo(40L);
        assertThat(((BigDecimal) unused.get("annualCost")).toPlainString()).isEqualTo("4800.00");
        assertThat(((BigDecimal) r.get("licenceAnnualSavings")).toPlainString()).isEqualTo("4800.00");

        Map<String, Object> over = finding(r, "LICENCE_OVER_ALLOCATED");
        assertThat(over.get("count")).isEqualTo(1L);
        assertThat(over.get("excessSeats")).isEqualTo(3L);
        assertThat(((BigDecimal) over.get("annualExposure")).toPlainString()).isEqualTo("300.00");
    }

    @Test
    @DisplayName("a caller who may not read licences never reaches the licence table")
    void licencesAreWithheldNotZeroed() {
        when(assetRepository.findValuationRows(org)).thenReturn(List.of());

        Map<String, Object> r = service.costWaste(org, null, null,
                EnumSet.of(InsightSection.ASSETS, InsightSection.VALUATION));

        verify(licenseRepository, never()).findByOrganisationAndDeletedAtIsNull(org);
        assertThat(findings(r).stream().map(f -> f.get("key")))
                .doesNotContain("LICENCE_UNUSED_SEATS", "LICENCE_OVER_ALLOCATED");
        assertThat(r.get("withheldSections")).isEqualTo(List.of("software licences"));
    }

    @Test
    @DisplayName("an empty tenant gets every rule at zero with nothing invented")
    void emptyTenantLooksSensible() {
        when(assetRepository.findValuationRows(org)).thenReturn(List.of());
        when(licenseRepository.findByOrganisationAndDeletedAtIsNull(org)).thenReturn(List.of());

        Map<String, Object> r = service.costWaste(org, null, null, everything);

        assertThat(r.get("distinctAssetsFlagged")).isEqualTo(0L);
        assertThat(((BigDecimal) r.get("capitalTiedUp")).toPlainString()).isEqualTo("0.00");
        assertThat(findings(r)).allSatisfy(f -> {
            assertThat(f.get("count")).isEqualTo(0L);
            assertThat((List<?>) f.get("items")).isEmpty();
            assertThat(f.get("truncated")).isEqualTo(false);
            assertThat(f.get("explanation")).asString().isNotBlank();
        });
        assertThat(r.get("complete")).isEqualTo(true);
    }

    @Test
    @DisplayName("items carry the identifiers the UI links on, and are truncated honestly")
    void itemsAreLinkableAndTruncationIsDeclared() {
        UUID dept = UUID.randomUUID();
        when(assetRepository.findValuationRows(org)).thenReturn(List.of(
                InsightTestFixtures.asset("A", "GHS", "100").unassigned().department(dept, "Ops").build(),
                InsightTestFixtures.asset("B", "GHS", "200").unassigned().department(dept, "Ops").build()));
        when(licenseRepository.findByOrganisationAndDeletedAtIsNull(org)).thenReturn(List.of());

        Map<String, Object> r = service.costWaste(org, null, 1, everything);
        Map<String, Object> unassigned = finding(r, "UNASSIGNED_IN_USE");

        assertThat(unassigned.get("count")).isEqualTo(2L);
        assertThat(unassigned.get("truncated")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) unassigned.get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("name")).isEqualTo("B");   // highest book value first
        assertThat(items.get(0).get("id")).isNotNull();
        assertThat(items.get(0).get("departmentId")).isEqualTo(dept);
        assertThat(items.get(0).get("resourceType")).isEqualTo("asset");
    }

    @Test
    @DisplayName("an out-of-range idleDays is rejected rather than clamped silently")
    void idleDaysIsValidated() {
        assertThatThrownBy(() -> service.costWaste(org, 0, null, everything))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.costWaste(org, null, 1000, everything))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SoftwareLicense licence(String name, String currency, String annualCost, int total, int used) {
        SoftwareLicense l = new SoftwareLicense();
        l.setId(UUID.randomUUID());
        l.setName(name);
        l.setVendor("Vendor");
        l.setLicenseType(LicenseType.values()[0]);
        l.setStatus(LicenseStatus.ACTIVE);
        l.setCurrency(currency);
        l.setAnnualRenewalCost(new BigDecimal(annualCost));
        l.setTotalSeats(total);
        l.setUsedSeats(used);
        l.setOrganisation(org);
        return l;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> findings(Map<String, Object> response) {
        return (List<Map<String, Object>>) response.get("findings");
    }
}
