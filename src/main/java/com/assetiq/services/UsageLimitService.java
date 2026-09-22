package com.assetiq.services;

import com.assetiq.models.Organisation;
import com.assetiq.models.SubscriptionPlan;

public interface UsageLimitService {
    void assertCanCreateAsset(Organisation organisation);

    void assertCanCreateEmployee(Organisation organisation);

    /**
     * Refuses reactivating a user when every seat the plan allows is already taken
     * by an active user. Reactivation used to skip the plan check entirely, so a
     * tenant could deactivate, downgrade, and reactivate its way past the limit.
     *
     * @throws org.springframework.security.access.AccessDeniedException when no seat is free
     */
    void assertCanActivateUser(Organisation organisation);

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
