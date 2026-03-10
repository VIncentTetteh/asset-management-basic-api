package com.example.demo.dto;

import com.example.demo.enums.SubscriptionStatus;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class OrganisationSubscriptionDto {
    private UUID id;
    private UUID organisationId;
    private SubscriptionPlanDto plan;
    private SubscriptionStatus status;
    private Boolean autoRenew;
    private Instant currentPeriodStart;
    private Instant currentPeriodEnd;
    private Instant nextBillingAt;
    private Long currentAssetCount;
    private Long currentEmployeeCount;
}

