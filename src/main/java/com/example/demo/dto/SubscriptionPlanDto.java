package com.example.demo.dto;

import com.example.demo.enums.BillingInterval;
import com.example.demo.enums.BillingPlanTier;
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
}

