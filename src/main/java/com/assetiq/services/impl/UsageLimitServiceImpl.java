package com.assetiq.services.impl;

import com.assetiq.license.LicensePlanLimitsService;
import com.assetiq.models.Organisation;
import com.assetiq.models.OrganisationSubscription;
import com.assetiq.models.SubscriptionPlan;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.OrganisationSubscriptionRepository;
import com.assetiq.repositories.SubscriptionPlanRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.services.UsageLimitService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
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
        SubscriptionPlan plan = resolvePlan(organisation);
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
        SubscriptionPlan plan = resolvePlan(organisation);
        long userCount = userRepository.countByOrganisationAndDeletedAtIsNull(organisation);
        if (userCount >= plan.getMaxEmployees()) {
            throw new AccessDeniedException("Employee limit reached for current plan. Upgrade your subscription.");
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
        SubscriptionPlan plan = resolvePlan(organisation);
        if (!Boolean.TRUE.equals(plan.getAnalyticsEnabled())) {
            throw new AccessDeniedException("Advanced analytics is available on paid plans only.");
        }
    }

    private SubscriptionPlan resolvePlan(Organisation organisation) {
        OrganisationSubscription subscription = organisationSubscriptionRepository
                .findFirstByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(organisation)
                .orElse(null);
        if (subscription != null && subscription.getPlan() != null
                && subscription.getStatus() == com.assetiq.enums.SubscriptionStatus.ACTIVE) {
            return subscription.getPlan();
        }
        return subscriptionPlanRepository.findByCodeAndDeletedAtIsNull("FREEMIUM")
                .orElseThrow(() -> new IllegalStateException("FREEMIUM plan is not configured"));
    }
}

