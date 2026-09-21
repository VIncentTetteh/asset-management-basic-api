package com.assetiq.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Outcome of a plan change request. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanChangeResponse {

    public enum Action {
        /** Upgrade: pay at {@code checkout.authorizationUrl}; the plan changes once paid. */
        CHECKOUT,
        /** Downgrade: takes effect at the end of the current period, no charge now. */
        SCHEDULED,
        /** Already on the requested plan. */
        NO_CHANGE
    }

    private Action action;
    private BillingCheckoutResponse checkout;
    private OrganisationSubscriptionDto subscription;
}
