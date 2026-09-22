package com.assetiq.services.impl;

import com.assetiq.enums.SubscriptionStatus;
import com.assetiq.license.LicensePlanLimitsService;
import com.assetiq.models.Organisation;
import com.assetiq.models.OrganisationSubscription;
import com.assetiq.models.SubscriptionPlan;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.OrganisationSubscriptionRepository;
import com.assetiq.repositories.SubscriptionPlanRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.services.UsageLimitService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Limit-enforcement checks. Marked {@code @Transactional(readOnly = true)} at
 * the class level because {@link #resolvePlan} walks through the lazy
 * {@code OrganisationSubscription → SubscriptionPlan} association; without an
 * open Hibernate session those proxy accesses throw
 * {@link org.hibernate.LazyInitializationException}.
 */
@Service
@Transactional(readOnly = true)
public class UsageLimitServiceImpl implements UsageLimitService {

    private final OrganisationSubscriptionRepository organisationSubscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;

    /**
     * Present only in standalone mode (APP_MODE=standalone).
     * When present, plan limits are read from the license key payload instead
     * of the Paystack subscription. In cloud mode this is Optional.empty()
     * and the existing Paystack-based logic runs unchanged.
     */
    private final Optional<LicensePlanLimitsService> licenseLimits;

    /**
     * Days of full paid access after a failed payment. Must match
     * {@code SubscriptionDunningJob}, which downgrades when this window closes; reading
     * the same property keeps the promise in the reminder email true.
     */
    @Value("${app.billing.dunning.grace-days:14}")
    private int graceDays = 14;

    private Clock clock = Clock.systemUTC();

    public UsageLimitServiceImpl(
            OrganisationSubscriptionRepository organisationSubscriptionRepository,
            SubscriptionPlanRepository subscriptionPlanRepository,
            AssetRepository assetRepository,
            UserRepository userRepository,
            Optional<LicensePlanLimitsService> licenseLimits) {
        this.organisationSubscriptionRepository = organisationSubscriptionRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.licenseLimits = licenseLimits;
    }

    @Override
    public void assertCanCreateAsset(Organisation organisation) {
        if (licenseLimits.isPresent()) {
            // Standalone mode: enforce limits from license key payload
            long count = assetRepository.countByOrganisationAndDeletedAtIsNull(organisation);
            licenseLimits.get().assertCanCreateAsset(count);
            return;
        }
        // Cloud mode: existing Paystack subscription logic unchanged
        SubscriptionPlan plan = resolveEffectivePlan(organisation);
        long assetCount = assetRepository.countByOrganisationAndDeletedAtIsNull(organisation);
        if (assetCount >= plan.getMaxAssets()) {
            throw new AccessDeniedException("Asset limit reached for current plan. Upgrade your subscription.");
        }
    }

    @Override
    public void assertCanCreateEmployee(Organisation organisation) {
        if (licenseLimits.isPresent()) {
            // Standalone mode: enforce limits from license key payload
            long count = userRepository.countByOrganisationAndDeletedAtIsNull(organisation);
            licenseLimits.get().assertCanCreateUser(count);
            return;
        }
        // Cloud mode: existing Paystack subscription logic unchanged
        SubscriptionPlan plan = resolveEffectivePlan(organisation);
        long userCount = userRepository.countByOrganisationAndDeletedAtIsNull(organisation);
        if (userCount >= plan.getMaxEmployees()) {
            throw new AccessDeniedException("Employee limit reached for current plan. Upgrade your subscription.");
        }
    }

    @Override
    public void assertCanActivateUser(Organisation organisation) {
        // A seat is an active user: deactivating frees one, reactivating takes one.
        long activeSeats = userRepository.countByOrganisationAndStatusAndDeletedAtIsNull(
                organisation, com.assetiq.enums.UserStatus.ACTIVE);
        if (licenseLimits.isPresent()) {
            licenseLimits.get().assertCanCreateUser(activeSeats);
            return;
        }
        SubscriptionPlan plan = resolveEffectivePlan(organisation);
        if (activeSeats >= plan.getMaxEmployees()) {
            throw new AccessDeniedException(
                    "All " + plan.getMaxEmployees() + " seats on your current plan are in use. "
                            + "Deactivate another user or upgrade your subscription.");
        }
    }

    @Override
    public void assertAdvancedAnalyticsAccess(Organisation organisation) {
        if (licenseLimits.isPresent()) {
            // Standalone mode: check feature flag from license payload
            if (!licenseLimits.get().isFeatureEnabled("apiAccess")) {
                throw new AccessDeniedException("Advanced analytics is not included in your license plan.");
            }
            return;
        }
        // Cloud mode: existing logic unchanged
        SubscriptionPlan plan = resolveEffectivePlan(organisation);
        if (!Boolean.TRUE.equals(plan.getAnalyticsEnabled())) {
            throw new AccessDeniedException("Advanced analytics is available on paid plans only.");
        }
    }

    @Override
    public SubscriptionPlan resolveEffectivePlan(Organisation organisation) {
        OrganisationSubscription subscription = organisationSubscriptionRepository
                .findFirstByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(organisation)
                .orElse(null);
        if (subscription != null && subscription.getPlan() != null && isEntitled(subscription)) {
            return subscription.getPlan();
        }
        return subscriptionPlanRepository.findByCodeAndDeletedAtIsNull("FREEMIUM")
                .orElseThrow(() -> new IllegalStateException("FREEMIUM plan is not configured"));
    }

    @Override
    public void assertUsageFitsPlan(Organisation organisation, SubscriptionPlan target) {
        List<String> overages = new ArrayList<>();
        long assets = assetRepository.countByOrganisationAndDeletedAtIsNull(organisation);
        if (target.getMaxAssets() != null && assets > target.getMaxAssets()) {
            overages.add(String.format("%d assets (the %s plan allows %d)",
                    assets, target.getName(), target.getMaxAssets()));
        }
        long users = userRepository.countByOrganisationAndDeletedAtIsNull(organisation);
        if (target.getMaxEmployees() != null && users > target.getMaxEmployees()) {
            overages.add(String.format("%d users (the %s plan allows %d)",
                    users, target.getName(), target.getMaxEmployees()));
        }
        if (!overages.isEmpty()) {
            throw new IllegalStateException("Your current usage does not fit the " + target.getName()
                    + " plan: " + String.join(", ", overages)
                    + ". Reduce usage before downgrading, or choose a larger plan.");
        }
    }

    /**
     * ACTIVE, or PAST_DUE still inside the grace window. The dunning job promises the
     * customer full access for {@link #graceDays} after a failed payment, so limits must
     * not drop to Freemium the moment the payment fails.
     */
    private boolean isEntitled(OrganisationSubscription subscription) {
        if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            return true;
        }
        if (subscription.getStatus() == SubscriptionStatus.PAST_DUE) {
            Instant since = subscription.getPastDueSince();
            return since == null
                    || Instant.now(clock).isBefore(since.plus(Duration.ofDays(graceDays)));
        }
        return false;
    }

    /** Test seam. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** Test seam. */
    void setGraceDays(int graceDays) {
        this.graceDays = graceDays;
    }
}
