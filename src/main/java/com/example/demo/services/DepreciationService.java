package com.example.demo.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface DepreciationService {
    /**
     * Calculate depreciation for an asset based on its category's policy
     */
    BigDecimal calculateDepreciation(UUID assetId);

    /**
     * Calculate depreciation for a specific date
     */
    BigDecimal calculateDepreciationAsOf(UUID assetId, LocalDate asOfDate);

    /**
     * Calculate monthly depreciation expense
     */
    BigDecimal calculateMonthlyDepreciation(UUID assetId);

    /**
     * Get current book value of an asset
     */
    BigDecimal getCurrentBookValue(UUID assetId);

    /**
     * Update book value after depreciation
     */
    void updateBookValue(UUID assetId);

    /**
     * Run monthly depreciation batch job for all assets
     */
    void runMonthlyDepreciationBatch();
}

