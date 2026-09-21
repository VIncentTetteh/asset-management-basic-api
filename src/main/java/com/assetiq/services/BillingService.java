package com.assetiq.services;

import com.assetiq.dto.BillingCheckoutRequest;
import com.assetiq.dto.BillingCheckoutResponse;
import com.assetiq.dto.OrganisationSubscriptionDto;
import com.assetiq.dto.PlanChangeResponse;
import com.assetiq.dto.SubscriptionPlanDto;

import java.util.List;

public interface BillingService {
    List<SubscriptionPlanDto> listPlans();

    OrganisationSubscriptionDto getCurrentSubscription();

    BillingCheckoutResponse initializeCheckout(BillingCheckoutRequest request);

    OrganisationSubscriptionDto verifyCheckout(String reference);

    OrganisationSubscriptionDto setAutoRenew(boolean enabled);

    /** Upgrade now (via checkout) or schedule a downgrade for the end of the period. */
    PlanChangeResponse changePlan(BillingCheckoutRequest request);

    /** Withdraws a scheduled downgrade and restores automatic renewal. */
    OrganisationSubscriptionDto cancelScheduledChange();

    void handlePaystackWebhook(String signature, String payload);
}

