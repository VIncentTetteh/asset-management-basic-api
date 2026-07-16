package com.assetiq.billing;

import com.assetiq.enums.SubscriptionStatus;
import com.assetiq.models.Organisation;
import com.assetiq.models.OrganisationSubscription;
import com.assetiq.models.SubscriptionPlan;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.OrganisationSubscriptionRepository;
import com.assetiq.repositories.SubscriptionPlanRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.services.impl.UsageLimitServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UsageLimitServiceImpl covering cloud (Paystack) mode.
 *
 * Key facts discovered from the source:
 *  - Limit violations throw AccessDeniedException (Spring Security), not a custom exception.
 *  - The plan field for employee cap is maxEmployees (not maxUsers).
 *  - Constructor requires 5 args: orgSubRepo, planRepo, assetRepo, userRepo, Optional<LicensePlanLimitsService>.
 *  - When no ACTIVE subscription is found, the service falls back to a FREEMIUM plan via planRepo.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UsageLimitServiceImpl")
class UsageLimitServiceImplTest {

    @Mock OrganisationSubscriptionRepository subscriptionRepository;
    @Mock SubscriptionPlanRepository         planRepository;
    @Mock AssetRepository                    assetRepository;
    @Mock UserRepository                     userRepository;

    UsageLimitServiceImpl service;
    Organisation org;

    @BeforeEach
    void setUp() {
        // Cloud mode: no standalone license limits
        service = new UsageLimitServiceImpl(
                subscriptionRepository, planRepository, assetRepository, userRepository, Optional.empty());

        org = new Organisation();
        org.setId(UUID.randomUUID());
        org.setName("Test Org");
    }

    // ============================================================
    // assertCanCreateAsset — cloud mode (Paystack subscription)
    // ============================================================

    @Test
    @DisplayName("assertCanCreateAsset passes when asset count is below plan limit")
    void assetCreate_belowLimit_passes() {
        attachActiveSubscription(org, 100, 50);
        when(assetRepository.countByOrganisationAndDeletedAtIsNull(org)).thenReturn(50L);

        assertThatCode(() -> service.assertCanCreateAsset(org)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertCanCreateAsset throws AccessDeniedException when asset count equals plan limit")
    void assetCreate_atLimit_throws() {
        attachActiveSubscription(org, 100, 50);
        when(assetRepository.countByOrganisationAndDeletedAtIsNull(org)).thenReturn(100L);

        assertThatThrownBy(() -> service.assertCanCreateAsset(org))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Asset limit");
    }

    @Test
    @DisplayName("assertCanCreateAsset throws AccessDeniedException when asset count exceeds plan limit")
    void assetCreate_overLimit_throws() {
        attachActiveSubscription(org, 10, 5);
        when(assetRepository.countByOrganisationAndDeletedAtIsNull(org)).thenReturn(15L);

        assertThatThrownBy(() -> service.assertCanCreateAsset(org))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("assertCanCreateAsset falls back to FREEMIUM plan when org has no active subscription")
    void assetCreate_noActiveSubscription_usesFreeumium() {
        // No subscription found → fall back to FREEMIUM
        when(subscriptionRepository.findFirstByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(org))
                .thenReturn(Optional.empty());

        SubscriptionPlan freemium = makePlan(5, 3);
        when(planRepository.findByCodeAndDeletedAtIsNull("FREEMIUM"))
                .thenReturn(Optional.of(freemium));
        when(assetRepository.countByOrganisationAndDeletedAtIsNull(org)).thenReturn(3L);

        assertThatCode(() -> service.assertCanCreateAsset(org)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertCanCreateAsset throws when freemium limit hit and no subscription")
    void assetCreate_freemiumLimit_throws() {
        when(subscriptionRepository.findFirstByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(org))
                .thenReturn(Optional.empty());

        SubscriptionPlan freemium = makePlan(5, 3);
        when(planRepository.findByCodeAndDeletedAtIsNull("FREEMIUM"))
                .thenReturn(Optional.of(freemium));
        when(assetRepository.countByOrganisationAndDeletedAtIsNull(org)).thenReturn(5L);

        assertThatThrownBy(() -> service.assertCanCreateAsset(org))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Asset limit");
    }

    // ============================================================
    // assertCanCreateEmployee — cloud mode
    // ============================================================

    @Test
    @DisplayName("assertCanCreateEmployee passes when user count is below plan limit")
    void employeeCreate_belowLimit_passes() {
        attachActiveSubscription(org, 100, 10);
        when(userRepository.countByOrganisationAndDeletedAtIsNull(org)).thenReturn(5L);

        assertThatCode(() -> service.assertCanCreateEmployee(org)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertCanCreateEmployee throws AccessDeniedException when user count equals maxEmployees")
    void employeeCreate_atLimit_throws() {
        attachActiveSubscription(org, 100, 10);
        when(userRepository.countByOrganisationAndDeletedAtIsNull(org)).thenReturn(10L);

        assertThatThrownBy(() -> service.assertCanCreateEmployee(org))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Employee limit");
    }

    @Test
    @DisplayName("assertCanCreateEmployee throws when user count exceeds maxEmployees")
    void employeeCreate_overLimit_throws() {
        attachActiveSubscription(org, 100, 3);
        when(userRepository.countByOrganisationAndDeletedAtIsNull(org)).thenReturn(7L);

        assertThatThrownBy(() -> service.assertCanCreateEmployee(org))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ============================================================
    // assertAdvancedAnalyticsAccess
    // ============================================================

    @Test
    @DisplayName("assertAdvancedAnalyticsAccess passes when plan has analytics enabled")
    void analyticsAccess_enabled_passes() {
        attachActiveSubscription(org, 100, 50, true);

        assertThatCode(() -> service.assertAdvancedAnalyticsAccess(org)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertAdvancedAnalyticsAccess throws when plan does not include analytics")
    void analyticsAccess_disabled_throws() {
        attachActiveSubscription(org, 100, 50, false);

        assertThatThrownBy(() -> service.assertAdvancedAnalyticsAccess(org))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("analytics");
    }

    // ============================================================
    // Helpers
    // ============================================================

    /**
     * Attaches an ACTIVE subscription with the given plan limits to the mock repository.
     */
    private void attachActiveSubscription(Organisation org, int maxAssets, int maxEmployees) {
        attachActiveSubscription(org, maxAssets, maxEmployees, false);
    }

    private void attachActiveSubscription(Organisation org, int maxAssets, int maxEmployees, boolean analyticsEnabled) {
        SubscriptionPlan plan = makePlan(maxAssets, maxEmployees);
        plan.setAnalyticsEnabled(analyticsEnabled);

        OrganisationSubscription sub = new OrganisationSubscription();
        sub.setOrganisation(org);
        sub.setPlan(plan);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setCurrentPeriodStart(Instant.now().minusSeconds(86400));
        sub.setCurrentPeriodEnd(Instant.now().plusSeconds(86400 * 30L));

        when(subscriptionRepository.findFirstByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(org))
                .thenReturn(Optional.of(sub));
    }

    private SubscriptionPlan makePlan(int maxAssets, int maxEmployees) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setMaxAssets(maxAssets);
        plan.setMaxEmployees(maxEmployees);
        plan.setAnalyticsEnabled(false);
        return plan;
    }
}
