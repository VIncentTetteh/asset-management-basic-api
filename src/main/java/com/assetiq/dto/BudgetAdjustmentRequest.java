package com.assetiq.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * POST /budgets/{id}/adjustment
 * For direct charges that bypass the expense workflow (e.g. vendor invoices paid
 * outside the system). The mandatory note prevents accidental double-counting.
 */
public record BudgetAdjustmentRequest(
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @NotBlank String note
) {}
