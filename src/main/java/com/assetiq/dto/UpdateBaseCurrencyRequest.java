package com.assetiq.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code PUT /api/v1/currency/settings}. */
public record UpdateBaseCurrencyRequest(@NotBlank(message = "baseCurrency is required") String baseCurrency) {
}
