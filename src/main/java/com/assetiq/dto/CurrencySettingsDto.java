package com.assetiq.dto;

import java.util.List;

/**
 * Tenant base-currency settings, returned by {@code GET/PUT /api/v1/currency/settings}.
 *
 * @param baseCurrency        ISO-4217 code every aggregate is converted into
 * @param availableCurrencies the base currency plus every currency appearing in this
 *                            organisation's exchange rates (as base or target), sorted
 * @param canEdit             whether the caller may change the base currency
 */
public record CurrencySettingsDto(String baseCurrency, List<String> availableCurrencies, boolean canEdit) {
}
