package com.assetiq.services;

import com.assetiq.dto.BillingCheckoutRequest;
import com.assetiq.dto.BillingCheckoutResponse;
import com.assetiq.dto.OrganisationSubscriptionDto;
import com.assetiq.dto.SubscriptionPlanDto;

import java.util.List;

public interface BillingService {
    List<SubscriptionPlanDto> listPlans();

    OrganisationSubscriptionDto getCurrentSubscription();

    BillingCheckoutResponse initializeCheckout(BillingCheckoutRequest request);

    OrganisationSubscriptionDto verifyCheckout(String reference);

    OrganisationSubscriptionDto setAutoRenew(boolean enabled);

    void handlePaystackWebhook(String signature, String payload);
}

