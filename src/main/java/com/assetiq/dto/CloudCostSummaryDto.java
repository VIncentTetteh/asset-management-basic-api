package com.assetiq.dto;

import com.assetiq.enums.CloudProvider;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class CloudCostSummaryDto {

    /** Total estimated monthly cost across all cloud assets */
    private BigDecimal totalMonthlyCost;

    /** ISO-4217 code every amount in this summary is expressed in (tenant base currency). */
    private String currency;

    /** False when at least one asset was excluded because no exchange rate exists. */
    private boolean complete;

    /** Sorted {@code "FROM->TO"} currency pairs that lacked an exchange rate. */
    private List<String> missingRates;

    /** Cost breakdown by provider */
    private Map<CloudProvider, BigDecimal> costByProvider;

    /** Cost breakdown by environment (DEV, STAGING, PROD) */
    private Map<String, BigDecimal> costByEnvironment;

    /** Top 5 most expensive assets, compared and reported in {@link #currency} */
    private List<CloudAssetCostEntry> topAssets;

    @Data
    public static class CloudAssetCostEntry {
        private String assetName;
        private String resourceType;
        private BigDecimal monthlyCost;
    }
}
