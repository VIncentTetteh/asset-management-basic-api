package com.assetiq.dto;

import com.assetiq.enums.BillingInterval;
import com.assetiq.enums.BillingPlanTier;
import lombok.Data;

@Data
public class SubscriptionPlanDto {
    private String code;
    private String name;
    private BillingPlanTier tier;
    private BillingInterval interval;
    private Long amountMinor;
    private String currency;
    private Integer maxAssets;
    private Integer maxEmployees;
    private Boolean analyticsEnabled;
    private Integer auditRetentionDays;
    private java.math.BigDecimal discountPercent;
}

