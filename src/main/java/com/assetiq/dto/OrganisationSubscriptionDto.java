package com.assetiq.dto;

import com.assetiq.enums.SubscriptionStatus;
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
    private Instant canceledAt;
    private Instant pastDueSince;
    /** While PAST_DUE, paid limits stay in force until this instant. */
    private Instant graceEndsAt;
    private SubscriptionPlanDto scheduledPlan;
    private Instant scheduledChangeAt;
    private Long currentAssetCount;
    private Long currentEmployeeCount;
    private Long currentDepartmentCount;
}

