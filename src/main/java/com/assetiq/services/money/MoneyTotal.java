package com.assetiq.services.money;

import java.math.BigDecimal;
import java.util.List;

/**
 * A money aggregate expressed in a single currency.
 *
 * @param amount       the converted sum, scale 2, HALF_EVEN
 * @param currency     ISO-4217 code of {@code amount} (the tenant base currency)
 * @param complete     {@code false} when at least one contributing amount was
 *                     excluded because no exchange rate was available
 * @param missingRates sorted, de-duplicated {@code "FROM->TO"} pairs that lacked a rate
 */
public record MoneyTotal(BigDecimal amount, String currency, boolean complete, List<String> missingRates) {

    public MoneyTotal {
        missingRates = List.copyOf(missingRates);
    }
}
