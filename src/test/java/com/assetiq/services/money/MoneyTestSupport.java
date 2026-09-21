package com.assetiq.services.money;

import com.assetiq.services.CurrencyResolver;
import com.assetiq.services.ExchangeRateService;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Builds a {@link MoneyAggregator} backed by a fixed rate table, for golden tests
 * of services that aggregate mixed-currency records.
 */
public final class MoneyTestSupport {

    private MoneyTestSupport() {
    }

    /**
     * @param ratesToBase multiplier converting one unit of the key currency into the
     *                    organisation's base currency; currencies absent from the map
     *                    have no rate
     */
    public static MoneyAggregator aggregatorWithRates(Map<String, String> ratesToBase) {
        ExchangeRateService rates = mock(ExchangeRateService.class, withSettings().strictness(
                org.mockito.quality.Strictness.LENIENT));
        when(rates.findRate(any(), anyString(), anyString(), any())).thenAnswer(inv -> {
            String from = inv.getArgument(1);
            String rate = ratesToBase.get(from);
            return rate == null ? Optional.empty() : Optional.of(new BigDecimal(rate));
        });
        return new MoneyAggregator(rates, mock(CurrencyResolver.class));
    }
}
