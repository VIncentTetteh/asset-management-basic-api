package com.example.demo.services.impl;

import com.example.demo.models.Organisation;
import com.example.demo.models.OrganisationSubscription;
import com.example.demo.models.SubscriptionPlan;
import com.example.demo.repositories.AssetRepository;
import com.example.demo.repositories.OrganisationSubscriptionRepository;
import com.example.demo.repositories.SubscriptionPlanRepository;
import com.example.demo.repositories.UserRepository;
import com.example.demo.services.UsageLimitService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class UsageLimitServiceImpl implements UsageLimitService {

    private final OrganisationSubscriptionRepository organisationSubscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;

    public UsageLimitServiceImpl(
            OrganisationSubscriptionRepository organisationSubscriptionRepository,
            SubscriptionPlanRepository subscriptionPlanRepository,
            AssetRepository assetRepository,
            UserRepository userRepository) {
        this.organisationSubscriptionRepository = organisationSubscriptionRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void assertCanCreateAsset(Organisation organisation) {
        SubscriptionPlan plan = resolvePlan(organisation);
        long assetCount = assetRepository.countByOrganisationAndDeletedAtIsNull(organisation);
        if (assetCount >= plan.getMaxAssets()) {
            throw new AccessDeniedException("Asset limit reached for current plan. Upgrade your subscription.");
        }
    }

    @Override
    public void assertCanCreateEmployee(Organisation organisation) {
        SubscriptionPlan plan = resolvePlan(organisation);
        long userCount = userRepository.countByOrganisationAndDeletedAtIsNull(organisation);
        if (userCount >= plan.getMaxEmployees()) {
            throw new AccessDeniedException("Employee limit reached for current plan. Upgrade your subscription.");
        }
    }

    @Override
    public void assertAdvancedAnalyticsAccess(Organisation organisation) {
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
                && subscription.getStatus() == com.example.demo.enums.SubscriptionStatus.ACTIVE) {
            return subscription.getPlan();
        }
        return subscriptionPlanRepository.findByCodeAndDeletedAtIsNull("FREEMIUM")
                .orElseThrow(() -> new IllegalStateException("FREEMIUM plan is not configured"));
    }
}

