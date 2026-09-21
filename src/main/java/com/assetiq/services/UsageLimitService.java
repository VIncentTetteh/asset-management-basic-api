package com.assetiq.services;

import com.assetiq.models.Organisation;
import com.assetiq.models.SubscriptionPlan;

public interface UsageLimitService {
    void assertCanCreateAsset(Organisation organisation);

    void assertCanCreateEmployee(Organisation organisation);

    void assertAdvancedAnalyticsAccess(Organisation organisation);

    /**
     * The plan whose limits apply right now: the subscribed plan while it is ACTIVE or
     * PAST_DUE inside the dunning grace window, otherwise Freemium.
     */
    SubscriptionPlan resolveEffectivePlan(Organisation organisation);

    /**
     * Refuses a voluntary move to {@code target} when current usage already exceeds its
     * limits, naming each overage so the customer knows what to reduce.
     *
     * @throws IllegalStateException when usage does not fit the target plan
     */
    void assertUsageFitsPlan(Organisation organisation, SubscriptionPlan target);
}
