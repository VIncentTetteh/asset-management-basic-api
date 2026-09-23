package com.assetiq.services.insights;

import com.assetiq.models.Organisation;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.ContractRepository;
import com.assetiq.repositories.LeaseRecordRepository;
import com.assetiq.repositories.MaintenanceRecordRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExpiryRadarService")
class ExpiryRadarServiceTest {

    @Mock AssetRepository assetRepository;
    @Mock MaintenanceRecordRepository maintenanceRepository;
    @Mock ContractRepository contractRepository;
    @Mock SoftwareLicenseRepository licenseRepository;
    @Mock LeaseRecordRepository leaseRepository;

    private ExpiryRadarService service;
    private Organisation org;
    private final Set<InsightSection> everything = EnumSet.allOf(InsightSection.class);

    @BeforeEach
    void setUp() {
        service = new ExpiryRadarService(assetRepository, maintenanceRepository, contractRepository,
                licenseRepository, leaseRepository,
                MoneyTestSupport.aggregatorWithRates(Map.of("USD", "10")));
        org = InsightTestFixtures.org("GHS");
        lenient().when(assetRepository.findWarrantyDueBy(eq(org), any())).thenReturn(List.of());
        lenient().when(assetRepository.findInsuranceDueBy(eq(org), any())).thenReturn(List.of());
        lenient().when(maintenanceRepository.findDueBy(eq(org), any())).thenReturn(List.of());
        lenient().when(contractRepository.findDueBy(eq(org), any())).thenReturn(List.of());
        lenient().when(licenseRepository.findDueBy(eq(org), any())).thenReturn(List.of());
        lenient().when(leaseRepository.findDueBy(eq(org), any())).thenReturn(List.of());
    }

    private DueRow due(String name, LocalDate date, String amount, String currency) {
        return new DueRow(UUID.randomUUID(), name, "REF", date,
                amount == null ? null : new BigDecimal(amount), null, currency,
                UUID.randomUUID(), "Related");
    }

    @Test
    @DisplayName("items land in the bucket their due date says, and overdue is its own bucket")
    void bucketsFollowTheDueDate() {
        LocalDate today = LocalDate.now();
        when(contractRepository.findDueBy(eq(org), any())).thenReturn(List.of(
                due("Lapsed", today.minusDays(5), "100", "GHS"),
                due("Today", today, "200", "GHS"),
                due("InAMonth", today.plusDays(40), "400", "GHS"),
                due("InTwoMonths", today.plusDays(70), "800", "GHS")));

        Map<String, Object> r = service.radar(org, 90, null, everything);
        Map<String, Object> contracts = stream(r, "CONTRACT");

        assertThat(contracts.get("overdue")).isEqualTo(1L);
        assertThat(contracts.get("dueWithinHorizon")).isEqualTo(3L);
        assertThat(bucketCount(contracts, "OVERDUE")).isEqualTo(1L);
        assertThat(bucketCount(contracts, "DUE_0_29")).isEqualTo(1L);
        assertThat(bucketCount(contracts, "DUE_30_59")).isEqualTo(1L);
        assertThat(bucketCount(contracts, "DUE_60_89")).isEqualTo(1L);
        assertThat(bucketValue(contracts, "DUE_60_89")).isEqualTo("800.00");
    }

    @Test
    @DisplayName("bucket values are converted, and an unconvertible one is refused not added raw")
    void bucketValuesAreConverted() {
        LocalDate today = LocalDate.now();
        when(licenseRepository.findDueBy(eq(org), any())).thenReturn(List.of(
                due("Priced in USD", today.plusDays(10), "50", "USD"),
                due("Priced in GHS", today.plusDays(11), "100", "GHS"),
                due("Priced in JPY", today.plusDays(12), "9999", "JPY")));

        Map<String, Object> r = service.radar(org, 30, null, everything);
        Map<String, Object> licences = stream(r, "LICENCE");

        assertThat(bucketCount(licences, "DUE_0_29")).isEqualTo(3L);
        assertThat(bucketValue(licences, "DUE_0_29")).isEqualTo("600.00");   // 50*10 + 100
        assertThat(r.get("complete")).isEqualTo(false);
        assertThat(r.get("missingRates")).isEqualTo(List.of("JPY->GHS"));
    }

    @Test
    @DisplayName("a stream the caller may not read is omitted, never shown as zero")
    void withheldStreamsAreNotQueried() {
        Map<String, Object> r = service.radar(org, 90, null,
                EnumSet.of(InsightSection.ASSETS, InsightSection.VALUATION));

        verify(contractRepository, never()).findDueBy(any(), any());
        verify(licenseRepository, never()).findDueBy(any(), any());
        assertThat(streamKeys(r)).containsExactly("WARRANTY", "INSURANCE");
        assertThat(r.get("withheldSections")).isEqualTo(
                List.of("contracts", "leases", "maintenance records", "software licences"));
    }

    @Test
    @DisplayName("every stream says what its money figure means")
    void everyStreamExplainsItsValue() {
        Map<String, Object> r = service.radar(org, 90, null, everything);

        assertThat(streams(r)).allSatisfy(s ->
                assertThat(s.get("valueMeaning")).asString().isNotBlank());
    }

    @Test
    @DisplayName("an empty tenant gets an empty radar, with real buckets and zero counts")
    void emptyTenantLooksSensible() {
        Map<String, Object> r = service.radar(org, 90, null, everything);

        assertThat(asMap(r.get("totals"))).containsEntry("overdue", 0L)
                .containsEntry("dueWithinHorizon", 0L);
        assertThat(streams(r)).hasSize(6);
        assertThat(streams(r)).allSatisfy(s -> {
            assertThat(s.get("overdue")).isEqualTo(0L);
            assertThat((List<?>) s.get("items")).isEmpty();
            assertThat((List<?>) s.get("buckets")).isNotEmpty();
        });
        assertThat(r.get("complete")).isEqualTo(true);
    }

    @Test
    @DisplayName("a caller who may not see money gets dates and counts only")
    void moneyIsWithheld() {
        when(contractRepository.findDueBy(eq(org), any())).thenReturn(List.of(
                due("Lapsed", LocalDate.now().minusDays(1), "100", "GHS")));

        Map<String, Object> r = service.radar(org, 90, null,
                EnumSet.of(InsightSection.CONTRACTS));
        Map<String, Object> contracts = stream(r, "CONTRACT");

        assertThat(contracts.get("overdue")).isEqualTo(1L);
        assertThat(buckets(contracts).get(0)).doesNotContainKey("value");
        assertThat(items(contracts).get(0)).doesNotContainKey("value");
        assertThat(items(contracts).get(0)).containsKeys("id", "dueDate", "daysUntil");
    }

    @Test
    @DisplayName("an out-of-range horizon is rejected rather than clamped silently")
    void horizonIsValidated() {
        assertThatThrownBy(() -> service.radar(org, 0, null, everything))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.radar(org, 5000, null, everything))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> streams(Map<String, Object> r) {
        return (List<Map<String, Object>>) r.get("streams");
    }

    private static List<String> streamKeys(Map<String, Object> r) {
        return streams(r).stream().map(s -> (String) s.get("key")).toList();
    }

    private static Map<String, Object> stream(Map<String, Object> r, String key) {
        return streams(r).stream().filter(s -> key.equals(s.get("key"))).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> buckets(Map<String, Object> stream) {
        return (List<Map<String, Object>>) stream.get("buckets");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> stream) {
        return (List<Map<String, Object>>) stream.get("items");
    }

    private static Object bucketCount(Map<String, Object> stream, String bucket) {
        return buckets(stream).stream().filter(b -> bucket.equals(b.get("bucket")))
                .findFirst().orElseThrow().get("count");
    }

    private static String bucketValue(Map<String, Object> stream, String bucket) {
        return ((BigDecimal) buckets(stream).stream().filter(b -> bucket.equals(b.get("bucket")))
                .findFirst().orElseThrow().get("value")).toPlainString();
    }
}
