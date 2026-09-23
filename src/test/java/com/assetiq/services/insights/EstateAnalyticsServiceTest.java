package com.assetiq.services.insights;

import com.assetiq.enums.AssetStatus;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.AssetRepository;
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
import static org.mockito.Mockito.when;

/**
 * Golden fixture, hand-computed. A GHS tenant holding GHS, USD (rate 15),
 * EUR (rate 16) and JPY (no rate) assets:
 *
 * <pre>
 *   A  GHS 1000  bought 12 months ago, 10-month life  -> accum 1000, NBV    0, monthly   0
 *   B  USD  100  bought  2 months ago, 10-month life  -> accum   20, NBV   80, monthly  10  (x15)
 *   C  EUR   50  no useful life at all                -> accum    0, NBV   50, monthly   0  (x16)
 *   D  JPY 10000 no useful life, and no rate          -> excluded from every money total
 *   E  GHS  500  DISPOSED                             -> off the books entirely
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EstateAnalyticsService")
class EstateAnalyticsServiceTest {

    private static final UUID FINANCE = UUID.randomUUID();
    private static final UUID OPS = UUID.randomUUID();

    @Mock AssetRepository assetRepository;

    private EstateAnalyticsService service;
    private Organisation org;
    private final Set<InsightSection> everything = EnumSet.allOf(InsightSection.class);

    @BeforeEach
    void setUp() {
        service = new EstateAnalyticsService(assetRepository,
                MoneyTestSupport.aggregatorWithRates(Map.of("USD", "15", "EUR", "16")));
        org = InsightTestFixtures.org("GHS");
    }

    private void givenTheGoldenEstate() {
        LocalDate today = LocalDate.now();
        when(assetRepository.findValuationRows(org)).thenReturn(List.of(
                InsightTestFixtures.asset("A", "GHS", "1000")
                        .depreciatedOver(10, today.minusMonths(12)).department(FINANCE, "Finance").build(),
                InsightTestFixtures.asset("B", "USD", "100")
                        .depreciatedOver(10, today.minusMonths(2)).department(FINANCE, "Finance").build(),
                InsightTestFixtures.asset("C", "EUR", "50").department(OPS, "Operations").build(),
                InsightTestFixtures.asset("D", "JPY", "10000").department(OPS, "Operations").build(),
                InsightTestFixtures.asset("E", "GHS", "500")
                        .status(AssetStatus.DISPOSED).department(FINANCE, "Finance").build()));
    }

    @Test
    @DisplayName("totals match the hand-computed fixture and are stated in the base currency")
    void totalsMatchTheFixture() {
        givenTheGoldenEstate();

        Map<String, Object> r = service.estate(org, "department", everything);

        assertThat(r.get("assetCount")).isEqualTo(4L);               // the disposed asset is off the books
        assertThat(money(r, "totalCost")).isEqualTo("3300.00");      // 1000 + 100*15 + 50*16
        assertThat(money(r, "accumulatedDepreciation")).isEqualTo("1300.00");  // 1000 + 20*15
        assertThat(money(r, "netBookValue")).isEqualTo("2000.00");   // 0 + 80*15 + 50*16
        assertThat(money(r, "monthlyDepreciation")).isEqualTo("150.00");       // 10*15
        assertThat(r.get("assetsFullyDepreciated")).isEqualTo(1L);
        assertThat(r.get("assetsMissingDepreciationSetup")).isEqualTo(2L);
        assertThat(r.get("currency")).isEqualTo("GHS");
    }

    @Test
    @DisplayName("an amount with no exchange rate is refused, not added raw")
    void refusesToSumAnUnconvertibleCurrency() {
        givenTheGoldenEstate();

        Map<String, Object> r = service.estate(org, "department", everything);

        // 10000 JPY is nowhere in the total, and the response says why.
        assertThat(money(r, "totalCost")).isEqualTo("3300.00");
        assertThat(r.get("complete")).isEqualTo(false);
        assertThat(r.get("missingRates")).isEqualTo(List.of("JPY->GHS"));
    }

    @Test
    @DisplayName("groups carry their id, their share, and a filter the UI can link on")
    void groupsAreLinkable() {
        givenTheGoldenEstate();

        Map<String, Object> r = service.estate(org, "department", everything);
        List<Map<String, Object>> groups = groups(r);

        Map<String, Object> finance = groups.stream()
                .filter(g -> "Finance".equals(g.get("name"))).findFirst().orElseThrow();
        assertThat(finance.get("id")).isEqualTo(FINANCE);
        assertThat(finance.get("count")).isEqualTo(2L);
        assertThat(((BigDecimal) finance.get("cost")).toPlainString()).isEqualTo("2500.00");
        assertThat((Double) finance.get("costShare")).isEqualTo(75.8);  // 2500 / 3300
        assertThat(finance.get("filter")).isEqualTo(Map.of("departmentId", FINANCE.toString()));

        // The groups add up to the total, because they are valued the same way.
        BigDecimal summed = groups.stream()
                .map(g -> (BigDecimal) g.get("cost"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(summed.toPlainString()).isEqualTo("3300.00");
    }

    @Test
    @DisplayName("assets with no department form a linkable 'no department' group")
    void unassignedIsItsOwnGroup() {
        when(assetRepository.findValuationRows(org)).thenReturn(List.of(
                InsightTestFixtures.asset("Orphan", "GHS", "100").build()));

        List<Map<String, Object>> groups = groups(service.estate(org, "department", everything));

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).get("name")).isEqualTo("No department");
        assertThat(groups.get(0).get("id")).isNull();
        assertThat(asMap(groups.get(0).get("filter"))).containsEntry("departmentId", null);
    }

    @Test
    @DisplayName("an empty tenant reads as empty, not as broken")
    void emptyTenantLooksSensible() {
        when(assetRepository.findValuationRows(org)).thenReturn(List.of());

        Map<String, Object> r = service.estate(org, "department", everything);

        assertThat(r.get("assetCount")).isEqualTo(0L);
        assertThat(money(r, "totalCost")).isEqualTo("0.00");
        assertThat(money(r, "netBookValue")).isEqualTo("0.00");
        assertThat(r.get("complete")).isEqualTo(true);
        assertThat(groups(r)).isEmpty();
        assertThat(r.get("currency")).isEqualTo("GHS");
    }

    @Test
    @DisplayName("a caller who may not see money gets counts only, and is told what was withheld")
    void moneyIsWithheldFromACallerWhoMayNotSeeIt() {
        givenTheGoldenEstate();

        Map<String, Object> r = service.estate(org, "department",
                EnumSet.of(InsightSection.ASSETS, InsightSection.DEPARTMENTS));

        assertThat(r.get("assetCount")).isEqualTo(4L);
        assertThat(r).doesNotContainKeys("totalCost", "netBookValue");
        assertThat(groups(r).get(0)).doesNotContainKeys("cost", "netBookValue");
        assertThat(groups(r).get(0)).containsKey("count");
        assertThat(r.get("withheldSections")).isEqualTo(List.of("asset valuations"));
    }

    @Test
    @DisplayName("a caller who may not read assets gets nothing at all")
    void noAssetAccessNoEstate() {
        Map<String, Object> r = service.estate(org, "department", EnumSet.noneOf(InsightSection.class));

        assertThat(r.get("assetCount")).isEqualTo(0L);
        assertThat(groups(r)).isEmpty();
        assertThat(withheld(r)).contains("assets");
    }

    @Test
    @DisplayName("an unknown breakdown is rejected rather than silently defaulted")
    void unknownGroupByIsRejected() {
        assertThatThrownBy(() -> service.estate(org, "colour", everything))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("department, location, category, status, condition");
    }

    @Test
    @DisplayName("status and condition breakdowns use the enum name as the filter")
    void statusBreakdown() {
        when(assetRepository.findValuationRows(org)).thenReturn(List.of(
                InsightTestFixtures.asset("A", "GHS", "100").status(AssetStatus.IN_USE).build(),
                InsightTestFixtures.asset("B", "GHS", "300").status(AssetStatus.IN_STOCK).build()));

        List<Map<String, Object>> groups = groups(service.estate(org, "status", everything));

        assertThat(groups.get(0).get("name")).isEqualTo("IN_STOCK");   // sorted by cost
        assertThat(groups.get(0).get("filter")).isEqualTo(Map.of("status", "IN_STOCK"));
        assertThat((Double) groups.get(0).get("costShare")).isEqualTo(75.0);
    }

    @SuppressWarnings("unchecked")
    private static List<String> withheld(Map<String, Object> response) {
        return (List<String>) response.get("withheldSections");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> groups(Map<String, Object> response) {
        return (List<Map<String, Object>>) response.get("groups");
    }

    private static String money(Map<String, Object> response, String key) {
        return ((BigDecimal) response.get(key)).toPlainString();
    }
}
