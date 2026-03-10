package com.example.demo.services;

import com.example.demo.dto.BillingCheckoutRequest;
import com.example.demo.dto.BillingCheckoutResponse;
import com.example.demo.dto.OrganisationSubscriptionDto;
import com.example.demo.dto.SubscriptionPlanDto;

import java.util.List;

public interface BillingService {
    List<SubscriptionPlanDto> listPlans();

    OrganisationSubscriptionDto getCurrentSubscription();

    BillingCheckoutResponse initializeCheckout(BillingCheckoutRequest request);

    OrganisationSubscriptionDto verifyCheckout(String reference);

    OrganisationSubscriptionDto setAutoRenew(boolean enabled);

    void handlePaystackWebhook(String signature, String payload);
}

