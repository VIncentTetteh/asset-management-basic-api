package com.assetiq.services.insights;

import com.assetiq.enums.BudgetStatus;
import com.assetiq.models.Budget;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.BudgetRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BudgetBurnService")
class BudgetBurnServiceTest {

    @Mock BudgetRepository budgetRepository;

    private BudgetBurnService service;
    private Organisation org;
    private final Set<InsightSection> everything = EnumSet.allOf(InsightSection.class);

    @BeforeEach
    void setUp() {
        service = new BudgetBurnService(budgetRepository,
                MoneyTestSupport.aggregatorWithRates(Map.of("USD", "10")));
        org = InsightTestFixtures.org("GHS");
    }

    private Budget budget(String name, BudgetStatus status, String currency,
                          String total, String spent, String committed,
                          LocalDate start, LocalDate end) {
        Budget b = new Budget();
        b.setId(UUID.randomUUID());
        b.setName(name);
        b.setStatus(status);
        b.setCurrency(currency);
        b.setTotalAmount(new BigDecimal(total));
        b.setSpentAmount(new BigDecimal(spent));
        b.setCommittedAmount(new BigDecimal(committed));
        b.setPeriodStart(start);
        b.setPeriodEnd(end);
        b.setAlertThresholdPct(80);
        b.setOrganisation(org);
        return b;
    }

    @Test
    @DisplayName("burn counts commitments as spent, and available reflects both")
    void commitmentsCountAgainstTheBudget() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        when(budgetRepository.findOverlapping(eq(org), any(), any())).thenReturn(List.of(
                budget("IT", BudgetStatus.ACTIVE, "GHS", "10000", "6000", "3000", start, end)));

        Map<String, Object> r = service.burn(org, start, end, everything);

        assertThat(money(r, "totalBudget")).isEqualTo("10000.00");
        assertThat(money(r, "spent")).isEqualTo("6000.00");
        assertThat(money(r, "committed")).isEqualTo("3000.00");
        assertThat(money(r, "available")).isEqualTo("1000.00");
        assertThat(r.get("spentPercent")).isEqualTo(60.0);
        assertThat(r.get("committedPercent")).isEqualTo(30.0);
        assertThat(r.get("burnPercent")).isEqualTo(90.0);
        assertThat(r.get("budgetsOverThreshold")).isEqualTo(1L);
    }

    @Test
    @DisplayName("draft budgets are excluded: they approve nothing and would flatter the burn")
    void draftsAreExcluded() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        when(budgetRepository.findOverlapping(eq(org), any(), any())).thenReturn(List.of(
                budget("Live", BudgetStatus.ACTIVE, "GHS", "1000", "900", "0", start, end),
                budget("Wishlist", BudgetStatus.DRAFT, "GHS", "9000", "0", "0", start, end)));

        Map<String, Object> r = service.burn(org, start, end, everything);

        assertThat(r.get("budgetCount")).isEqualTo(1L);
        assertThat(money(r, "totalBudget")).isEqualTo("1000.00");
        assertThat(r.get("burnPercent")).isEqualTo(90.0);
    }

    @Test
    @DisplayName("budgets in other currencies are converted before they are added")
    void mixedCurrenciesAreConverted() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        when(budgetRepository.findOverlapping(eq(org), any(), any())).thenReturn(List.of(
                budget("Local", BudgetStatus.ACTIVE, "GHS", "1000", "500", "0", start, end),
                budget("Imported", BudgetStatus.ACTIVE, "USD", "100", "50", "0", start, end),
                budget("Unrateable", BudgetStatus.ACTIVE, "JPY", "99999", "1", "0", start, end)));

        Map<String, Object> r = service.burn(org, start, end, everything);

        assertThat(money(r, "totalBudget")).isEqualTo("2000.00");   // 1000 + 100*10; JPY refused
        assertThat(money(r, "spent")).isEqualTo("1000.00");
        assertThat(r.get("complete")).isEqualTo(false);
        assertThat(r.get("missingRates")).isEqualTo(List.of("JPY->GHS"));
        // The unconvertible budget's own row shows null rather than a fake zero.
        Map<String, Object> jpy = rows(r).stream()
                .filter(m -> "Unrateable".equals(m.get("name"))).findFirst().orElseThrow();
        assertThat(jpy.get("total")).isNull();
        // Its ratio is still true, because it is computed in its own currency.
        assertThat(jpy.get("burnPercent")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("pace compares burn against how much of the period has gone")
    void paceComparesBurnAgainstElapsedTime() {
        LocalDate start = LocalDate.now().minusDays(50);
        LocalDate end = LocalDate.now().plusDays(50);
        when(budgetRepository.findOverlapping(eq(org), any(), any())).thenReturn(List.of(
                budget("IT", BudgetStatus.ACTIVE, "GHS", "1000", "700", "0", start, end)));

        Map<String, Object> r = service.burn(org, start, end, everything);

        assertThat(r.get("periodElapsedPercent")).isEqualTo(50.0);
        assertThat(r.get("burnPercent")).isEqualTo(70.0);
        assertThat(r.get("pacePercentagePoints")).isEqualTo(20.0);
        assertThat(money(r, "projectedSpendAtPeriodEnd")).isEqualTo("1400.00");
    }

    @Test
    @DisplayName("no projection is offered in the first days of a period")
    void noProjectionWithoutEnoughElapsedTime() {
        LocalDate start = LocalDate.now().minusDays(1);
        LocalDate end = LocalDate.now().plusDays(364);
        when(budgetRepository.findOverlapping(eq(org), any(), any())).thenReturn(List.of(
                budget("IT", BudgetStatus.ACTIVE, "GHS", "1000", "50", "0", start, end)));

        Map<String, Object> r = service.burn(org, start, end, everything);

        assertThat(r.get("projectedSpendAtPeriodEnd")).isNull();
    }

    @Test
    @DisplayName("an empty tenant gets zeros and null ratios, never NaN")
    void emptyTenantLooksSensible() {
        when(budgetRepository.findOverlapping(eq(org), any(), any())).thenReturn(List.of());

        Map<String, Object> r = service.burn(org, null, null, everything);

        assertThat(r.get("budgetCount")).isEqualTo(0L);
        assertThat(money(r, "totalBudget")).isEqualTo("0.00");
        assertThat(money(r, "available")).isEqualTo("0.00");
        assertThat(r.get("burnPercent")).isNull();
        assertThat(r.get("spentPercent")).isNull();
        assertThat(r.get("pacePercentagePoints")).isNull();
        assertThat(rows(r)).isEmpty();
    }

    @Test
    @DisplayName("a caller who may not read budgets never reaches the budget table")
    void budgetsAreWithheld() {
        Map<String, Object> r = service.burn(org, null, null, EnumSet.of(InsightSection.ASSETS));

        verify(budgetRepository, never()).findOverlapping(any(), any(), any());
        assertThat(r.get("budgetCount")).isEqualTo(0L);
        assertThat(rows(r)).isEmpty();
        assertThat(r.get("withheldSections")).isEqualTo(List.of("budgets"));
    }

    @Test
    @DisplayName("a backwards window is rejected")
    void backwardsWindowIsRejected() {
        assertThatThrownBy(() -> service.burn(org,
                LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1), everything))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> r) {
        return (List<Map<String, Object>>) r.get("budgets");
    }

    private static String money(Map<String, Object> r, String key) {
        return ((BigDecimal) r.get(key)).toPlainString();
    }
}
