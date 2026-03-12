package com.example.demo.dto;

import com.example.demo.enums.CloudProvider;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class CloudCostSummaryDto {

    /** Total estimated monthly cost across all cloud assets */
    private BigDecimal totalMonthlyCost;

    private String currency;

    /** Cost breakdown by provider */
    private Map<CloudProvider, BigDecimal> costByProvider;

    /** Cost breakdown by environment (DEV, STAGING, PROD) */
    private Map<String, BigDecimal> costByEnvironment;

    /** Top 5 most expensive assets */
    private List<CloudAssetCostEntry> topAssets;

    @Data
    public static class CloudAssetCostEntry {
        private String assetName;
        private String resourceType;
        private BigDecimal monthlyCost;
    }
}
