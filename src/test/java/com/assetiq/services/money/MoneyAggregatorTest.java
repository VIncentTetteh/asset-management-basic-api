package com.assetiq.services.money;

import com.assetiq.models.Organisation;
import com.assetiq.services.CurrencyResolver;
import com.assetiq.services.ExchangeRateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Money aggregation")
class MoneyAggregatorTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 9, 21);

    @Mock ExchangeRateService exchangeRateService;
    @Mock CurrencyResolver currencyResolver;

    /** Rate table keyed by source currency; records every lookup for memoisation checks. */
    private final Map<String, BigDecimal> rates = new HashMap<>();
    private final List<String> lookups = new ArrayList<>();

    private CurrencyConversion ghs() {
        return new CurrencyConversion("ghs", AS_OF, (from, to, asOf) -> {
            lookups.add(from + "->" + to + "@" + asOf);
            return Optional.ofNullable(rates.get(from));
        });
    }

    @Test
    @DisplayName("same-currency amounts are summed without any rate lookup")
    void sameCurrency_noLookup() {
        CurrencyConversion fx = ghs();
        MoneyAccumulator acc = fx.newAccumulator();
        acc.add(new BigDecimal("100.10"), "GHS");
        acc.add(new BigDecimal("0.90"), "ghs");

        MoneyTotal total = acc.total();
        assertThat(total.amount()).isEqualByComparingTo("101.00");
        assertThat(total.amount().scale()).isEqualTo(2);
        assertThat(total.currency()).isEqualTo("GHS");
        assertThat(total.complete()).isTrue();
        assertThat(total.missingRates()).isEmpty();
        assertThat(lookups).isEmpty();
    }

    @Test
    @DisplayName("foreign amounts are converted with the rate, looked up once per currency")
    void directRate_convertsAndMemoises() {
        rates.put("USD", new BigDecimal("15.00"));
        CurrencyConversion fx = ghs();
        MoneyAccumulator acc = fx.newAccumulator();
        acc.add(new BigDecimal("10"), "USD");
        acc.add(new BigDecimal("2"), "USD");
        acc.add(new BigDecimal("5"), "GHS");
        fx.newAccumulator().add(BigDecimal.ONE, "USD");

        assertThat(acc.total().amount()).isEqualByComparingTo("185.00"); // 12*15 + 5
        assertThat(lookups).containsExactly("USD->GHS@" + AS_OF);
    }

    @Test
    @DisplayName("amounts with no rate are excluded, never added raw, and flagged")
    void missingRate_excludedAndFlagged() {
        rates.put("USD", new BigDecimal("15"));
        CurrencyConversion fx = ghs();
        MoneyAccumulator acc = fx.newAccumulator();
        assertThat(acc.add(new BigDecimal("100"), "GHS")).isTrue();
        assertThat(acc.add(new BigDecimal("999999"), "JPY")).isFalse();
        assertThat(acc.add(new BigDecimal("1"), "EUR")).isFalse();
        acc.add(new BigDecimal("2"), "USD");

        MoneyTotal total = acc.total();
        assertThat(total.amount()).isEqualByComparingTo("130.00");
        assertThat(total.complete()).isFalse();
        assertThat(total.missingRates()).containsExactly("EUR->GHS", "JPY->GHS");
        assertThat(acc.includedCount()).isEqualTo(2);
        assertThat(fx.isComplete()).isFalse();
        assertThat(fx.missingRates()).containsExactly("EUR->GHS", "JPY->GHS");
    }

    @Test
    @DisplayName("null or blank currency is treated as the base currency; null amounts add nothing")
    void nullCurrency_isBase() {
        CurrencyConversion fx = ghs();
        MoneyAccumulator acc = fx.newAccumulator();
        acc.add(new BigDecimal("40"), null);
        acc.add(new BigDecimal("2"), "  ");
        acc.add(null, "JPY");

        MoneyTotal total = acc.total();
        assertThat(total.amount()).isEqualByComparingTo("42.00");
        assertThat(total.complete()).isTrue();
        assertThat(lookups).isEmpty();
    }

    @Test
    @DisplayName("totals are rounded once, to 2 places, HALF_EVEN")
    void rounding_halfEvenOnFinalSum() {
        rates.put("USD", new BigDecimal("0.3333"));
        CurrencyConversion fx = ghs();

        MoneyAccumulator even = fx.newAccumulator();
        even.add(new BigDecimal("0.125"), "GHS");
        assertThat(even.amount()).isEqualByComparingTo("0.12");

        MoneyAccumulator odd = fx.newAccumulator();
        odd.add(new BigDecimal("0.135"), "GHS");
        assertThat(odd.amount()).isEqualByComparingTo("0.14");

        // 3 x (1 USD * 0.3333) = 0.9999 -> 1.00; per-item rounding would give 0.99
        MoneyAccumulator sum = fx.newAccumulator();
        sum.add(BigDecimal.ONE, "USD");
        sum.add(BigDecimal.ONE, "USD");
        sum.add(BigDecimal.ONE, "USD");
        assertThat(sum.amount()).isEqualByComparingTo("1.00");
        assertThat(sum.amount().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("putMetadata adds currency, complete and missingRates")
    void putMetadata_addsResponseKeys() {
        CurrencyConversion fx = ghs();
        fx.newAccumulator().add(BigDecimal.TEN, "XOF");

        Map<String, Object> response = fx.putMetadata(new LinkedHashMap<>());

        assertThat(response)
                .containsEntry("currency", "GHS")
                .containsEntry("complete", false)
                .containsEntry("missingRates", List.of("XOF->GHS"));
    }

    @Test
    @DisplayName("MoneyAggregator uses the organisation's billing currency as base")
    void aggregator_usesOrganisationBillingCurrency() {
        Organisation org = new Organisation();
        org.setId(UUID.randomUUID());
        org.setBillingCurrency(" eur ");
        when(exchangeRateService.findRate(org, "USD", "EUR", AS_OF))
                .thenReturn(Optional.of(new BigDecimal("0.5")));
        MoneyAggregator aggregator = new MoneyAggregator(exchangeRateService, currencyResolver);

        CurrencyConversion fx = aggregator.begin(org, AS_OF);
        MoneyAccumulator acc = fx.newAccumulator();
        acc.add(new BigDecimal("10"), "USD");

        assertThat(fx.baseCurrency()).isEqualTo("EUR");
        assertThat(acc.amount()).isEqualByComparingTo("5.00");
        verify(currencyResolver, never()).defaultFor(any());
    }

    @Test
    @DisplayName("MoneyAggregator falls back to the resolver when the org has no billing currency")
    void aggregator_fallsBackToResolver() {
        Organisation org = new Organisation();
        org.setId(UUID.randomUUID());
        when(currencyResolver.defaultFor(org.getId())).thenReturn("USD");

        assertThat(new MoneyAggregator(exchangeRateService, currencyResolver).begin(org).baseCurrency())
                .isEqualTo("USD");
    }
}
