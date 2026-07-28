package com.assetiq.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link CurrencyResolver#currencyForCountry(String)}.
 *
 * <p>Track A1 (multi-currency): the global platform default is USD, but a known
 * country must still map to its local currency. These tests pin the two edges
 * that a naive "flip the default to USD" change would silently break:
 * <ul>
 *   <li>Ghana ("GH") → GHS — Ghana is not otherwise special-cased and must
 *       not fall through to the USD default.</li>
 *   <li>An unlisted / null country → USD — the global-first fallback.</li>
 * </ul>
 */
class CurrencyResolverTest {

    @Test
    void ghana_mapsToGhs_notTheGlobalDefault() {
        assertEquals("GHS", CurrencyResolver.currencyForCountry("GH"));
        assertEquals("GHS", CurrencyResolver.currencyForCountry("Ghana"));
    }

    @Test
    void knownCountries_mapToLocalCurrency() {
        assertEquals("USD", CurrencyResolver.currencyForCountry("US"));
        assertEquals("GBP", CurrencyResolver.currencyForCountry("United Kingdom"));
        assertEquals("EUR", CurrencyResolver.currencyForCountry("DE"));
        assertEquals("NGN", CurrencyResolver.currencyForCountry("NG"));
        assertEquals("CAD", CurrencyResolver.currencyForCountry("Canada"));
    }

    @Test
    void anyIsoCountry_resolvesViaJdkTable_notJustAShortList() {
        // The old hand-maintained switch only covered ~15 countries and would
        // wrongly fall back to USD for everything else. The JDK ISO table covers
        // the whole world — these are the cases the short list got wrong.
        assertEquals("AUD", CurrencyResolver.currencyForCountry("AU"));
        assertEquals("AUD", CurrencyResolver.currencyForCountry("Australia"));
        assertEquals("JPY", CurrencyResolver.currencyForCountry("JP"));
        assertEquals("INR", CurrencyResolver.currencyForCountry("IN"));
        assertEquals("BRL", CurrencyResolver.currencyForCountry("BR"));
        assertEquals("CHF", CurrencyResolver.currencyForCountry("CH"));
    }

    @Test
    void nullOrUnlistedCountry_fallsBackToUsd() {
        assertEquals("USD", CurrencyResolver.currencyForCountry(null));
        assertEquals("USD", CurrencyResolver.currencyForCountry("Atlantis"));
        assertEquals("USD", CurrencyResolver.currencyForCountry(""));
    }

    @Test
    void countryCode_isCaseAndWhitespaceInsensitive() {
        assertEquals("GHS", CurrencyResolver.currencyForCountry("  gh  "));
        assertEquals("USD", CurrencyResolver.currencyForCountry("us"));
    }
}
