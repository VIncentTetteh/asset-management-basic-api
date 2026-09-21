package com.assetiq.services.money;

import com.assetiq.models.Organisation;
import com.assetiq.services.CurrencyResolver;
import com.assetiq.services.ExchangeRateService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Entry point for every backend money aggregate.
 *
 * <p>Records carry their own currency, so summing raw amounts produces a
 * meaningless figure once a tenant holds more than one currency. Aggregates
 * open a {@link CurrencyConversion} in the tenant's base currency
 * ({@link Organisation#getBillingCurrency()}, falling back to
 * {@link CurrencyResolver}'s platform default) and add every amount through it.
 * Rates come from the tenant's own {@code exchange_rates} table as of today;
 * amounts with no rate are excluded and reported, never added unconverted.
 */
@Component
public class MoneyAggregator {

    private final ExchangeRateService exchangeRateService;
    private final CurrencyResolver currencyResolver;

    public MoneyAggregator(ExchangeRateService exchangeRateService, CurrencyResolver currencyResolver) {
        this.exchangeRateService = exchangeRateService;
        this.currencyResolver = currencyResolver;
    }

    /** Start a conversion pass for {@code org} as of today. */
    public CurrencyConversion begin(Organisation org) {
        return begin(org, LocalDate.now());
    }

    /** Start a conversion pass for {@code org} using rates effective on or before {@code asOf}. */
    public CurrencyConversion begin(Organisation org, LocalDate asOf) {
        return new CurrencyConversion(baseCurrencyOf(org), asOf,
                (from, to, date) -> exchangeRateService.findRate(org, from, to, date));
    }

    /**
     * Start a conversion pass into an arbitrary {@code targetCurrency} (e.g. one
     * asset's own currency for its TCO), using the tenant's rates as of today.
     * Falls back to the tenant base currency when {@code targetCurrency} is blank.
     */
    public CurrencyConversion beginIn(Organisation org, String targetCurrency) {
        String target = targetCurrency != null && !targetCurrency.isBlank()
                ? targetCurrency : baseCurrencyOf(org);
        return new CurrencyConversion(target, LocalDate.now(),
                (from, to, date) -> exchangeRateService.findRate(org, from, to, date));
    }

    /** The tenant's base (reporting) currency. */
    public String baseCurrencyOf(Organisation org) {
        if (org != null && org.getBillingCurrency() != null && !org.getBillingCurrency().isBlank()) {
            return org.getBillingCurrency().trim().toUpperCase(Locale.ROOT);
        }
        return currencyResolver.defaultFor(org != null ? org.getId() : null);
    }
}
