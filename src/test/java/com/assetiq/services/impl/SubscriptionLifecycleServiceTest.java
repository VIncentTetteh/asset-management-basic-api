package com.assetiq.services.impl;

import com.assetiq.enums.BillingInterval;
import com.assetiq.enums.BillingPlanTier;
import com.assetiq.enums.SubscriptionStatus;
import com.assetiq.models.Organisation;
import com.assetiq.models.OrganisationSubscription;
import com.assetiq.models.SubscriptionPlan;
import com.assetiq.repositories.OrganisationSubscriptionRepository;
import com.assetiq.repositories.SubscriptionPlanRepository;
import com.assetiq.security.SecretCryptoService;
import com.assetiq.services.NotificationService;
import com.assetiq.services.impl.SubscriptionLifecycleService.ChangeDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SubscriptionLifecycleService")
class SubscriptionLifecycleServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

    @Mock OrganisationSubscriptionRepository subscriptionRepository;
    @Mock SubscriptionPlanRepository planRepository;
    @Mock PaystackGatewayService paystackGatewayService;
    @Mock SecretCryptoService secretCryptoService;
    @Mock NotificationService notificationService;

    SubscriptionLifecycleService service;
    Organisation org;
    SubscriptionPlan freemium;
    SubscriptionPlan basic;
    SubscriptionPlan business;
    SubscriptionPlan businessAnnual;

    @BeforeEach
    void setUp() {
        service = new SubscriptionLifecycleService(subscriptionRepository, planRepository,
                paystackGatewayService, secretCryptoService, notificationService);
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));

        org = new Organisation();
        org.setId(UUID.randomUUID());
        org.setName("Kwabenya Depot Ltd");

        freemium = plan("FREEMIUM", BillingPlanTier.FREEMIUM, 0L, BillingInterval.MONTHLY);
        basic = plan("BASIC", BillingPlanTier.BASIC, 10_000L, BillingInterval.MONTHLY);
        business = plan("BUSINESS", BillingPlanTier.BUSINESS, 20_000L, BillingInterval.MONTHLY);
        businessAnnual = plan("BUSINESS_ANNUAL", BillingPlanTier.BUSINESS, 216_000L, BillingInterval.ANNUALLY);
        when(planRepository.findByCodeAndDeletedAtIsNull("FREEMIUM")).thenReturn(Optional.of(freemium));
        when(secretCryptoService.decrypt(anyString())).thenAnswer(inv -> "plain-" + inv.getArgument(0));
    }

    private static SubscriptionPlan plan(String code, BillingPlanTier tier, long amount, BillingInterval interval) {
        SubscriptionPlan p = new SubscriptionPlan();
        p.setId(UUID.randomUUID());
        p.setCode(code);
        p.setName(code.charAt(0) + code.substring(1).toLowerCase());
        p.setTier(tier);
        p.setAmountMinor(amount);
        p.setCurrency("GHS");
        p.setInterval(interval);
        p.setActive(true);
        return p;
    }

    private OrganisationSubscription subscription(SubscriptionPlan plan, SubscriptionStatus status,
                                                  Instant start, Instant end) {
        OrganisationSubscription s = new OrganisationSubscription();
        s.setOrganisation(org);
        s.setPlan(plan);
        s.setStatus(status);
        s.setAutoRenew(true);
        s.setCurrentPeriodStart(start);
        s.setCurrentPeriodEnd(end);
        return s;
    }

    @Nested
    @DisplayName("classify")
    class Classify {

        @Test
        void freeToPaidIsAnUpgrade() {
            var s = subscription(freemium, SubscriptionStatus.ACTIVE, NOW, NOW.plus(365, ChronoUnit.DAYS));
            assertThat(service.classify(s, basic)).isEqualTo(ChangeDirection.UPGRADE);
        }

        @Test
        void cheaperPaidPlanIsADowngrade() {
            var s = subscription(business, SubscriptionStatus.ACTIVE, NOW, NOW.plus(30, ChronoUnit.DAYS));
            assertThat(service.classify(s, basic)).isEqualTo(ChangeDirection.DOWNGRADE);
        }

        @Test
        void paidToFreemiumIsADowngrade() {
            var s = subscription(basic, SubscriptionStatus.ACTIVE, NOW, NOW.plus(30, ChronoUnit.DAYS));
            assertThat(service.classify(s, freemium)).isEqualTo(ChangeDirection.DOWNGRADE);
        }

        @Test
        void monthlyToAnnualComparesPerMonthPrice() {
            // 216,000 / 12 = 18,000 per month, cheaper than 20,000 monthly: a downgrade
            // (at period end), not an immediate charge.
            var monthly = subscription(business, SubscriptionStatus.ACTIVE, NOW, NOW.plus(30, ChronoUnit.DAYS));
            assertThat(service.classify(monthly, businessAnnual)).isEqualTo(ChangeDirection.DOWNGRADE);

            var annual = subscription(businessAnnual, SubscriptionStatus.ACTIVE, NOW, NOW.plus(365, ChronoUnit.DAYS));
            assertThat(service.classify(annual, business)).isEqualTo(ChangeDirection.UPGRADE);
        }

        @Test
        void samePlanIsNoChange() {
            var s = subscription(basic, SubscriptionStatus.ACTIVE, NOW, NOW.plus(30, ChronoUnit.DAYS));
            assertThat(service.classify(s, basic)).isEqualTo(ChangeDirection.SAME);
        }

        @Test
        void lapsedPaidPlanBuyingAnyPaidPlanIsAnUpgrade() {
            var s = subscription(business, SubscriptionStatus.PAST_DUE, NOW.minus(40, ChronoUnit.DAYS),
                    NOW.minus(10, ChronoUnit.DAYS));
            assertThat(service.classify(s, basic)).isEqualTo(ChangeDirection.UPGRADE);
        }
    }

    @Nested
    @DisplayName("applyPaidPeriod")
    class ApplyPaidPeriod {

        @Test
        void earlyRenewalExtendsFromTheCurrentEnd() {
            Instant end = NOW.plus(5, ChronoUnit.DAYS);
            var s = subscription(basic, SubscriptionStatus.ACTIVE, NOW.minus(25, ChronoUnit.DAYS), end);

            service.applyPaidPeriod(s, basic);

            assertThat(s.getCurrentPeriodEnd()).isEqualTo(end.plus(30, ChronoUnit.DAYS));
            assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        }

        @Test
        void renewalAfterLapseStartsNowAndClearsDunning() {
            var s = subscription(basic, SubscriptionStatus.PAST_DUE, NOW.minus(40, ChronoUnit.DAYS),
                    NOW.minus(10, ChronoUnit.DAYS));
            s.setPastDueSince(NOW.minus(10, ChronoUnit.DAYS));

            service.applyPaidPeriod(s, basic);

            assertThat(s.getCurrentPeriodStart()).isEqualTo(NOW);
            assertThat(s.getCurrentPeriodEnd()).isEqualTo(NOW.plus(30, ChronoUnit.DAYS));
            assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(s.getPastDueSince()).isNull();
        }

        @Test
        void upgradeCreditsUnusedTimeOnTheNewPlan() {
            // Half of a 10,000 Basic month unused = 5,000 of value, which buys a quarter of
            // a 20,000 Business month: 7.5 extra days.
            var s = subscription(basic, SubscriptionStatus.ACTIVE, NOW.minus(15, ChronoUnit.DAYS),
                    NOW.plus(15, ChronoUnit.DAYS));

            service.applyPaidPeriod(s, business);

            Duration expectedCredit = Duration.ofHours(180);
            assertThat(s.getPlan()).isEqualTo(business);
            assertThat(s.getCurrentPeriodStart()).isEqualTo(NOW);
            assertThat(s.getCurrentPeriodEnd())
                    .isEqualTo(NOW.plus(30, ChronoUnit.DAYS).plus(expectedCredit));
        }

        @Test
        void noCreditAcrossCurrencies() {
            var s = subscription(basic, SubscriptionStatus.ACTIVE, NOW.minus(15, ChronoUnit.DAYS),
                    NOW.plus(15, ChronoUnit.DAYS));
            business.setCurrency("USD");

            service.applyPaidPeriod(s, business);

            assertThat(s.getCurrentPeriodEnd()).isEqualTo(NOW.plus(30, ChronoUnit.DAYS));
        }

        @Test
        void payingClearsAPendingDowngradeAndCancellation() {
            var s = subscription(business, SubscriptionStatus.ACTIVE, NOW.minus(1, ChronoUnit.DAYS),
                    NOW.plus(29, ChronoUnit.DAYS));
            s.setScheduledPlan(freemium);
            s.setScheduledChangeAt(s.getCurrentPeriodEnd());
            s.setAutoRenew(false);
            s.setCanceledAt(NOW);

            service.applyPaidPeriod(s, business);

            assertThat(s.getScheduledPlan()).isNull();
            assertThat(s.getScheduledChangeAt()).isNull();
            assertThat(s.getCanceledAt()).isNull();
            assertThat(s.getAutoRenew()).isTrue();
        }
    }

    @Nested
    @DisplayName("processEndedPeriod")
    class ProcessEndedPeriod {

        private final Instant ended = NOW.minus(1, ChronoUnit.HOURS);

        @Test
        void freemiumSimplyRollsOver() {
            var s = subscription(freemium, SubscriptionStatus.ACTIVE, ended.minus(365, ChronoUnit.DAYS), ended);

            assertThat(service.processEndedPeriod(s, NOW)).isTrue();

            assertThat(s.getPlan()).isEqualTo(freemium);
            assertThat(s.getCurrentPeriodEnd()).isAfter(NOW);
        }

        @Test
        void scheduledDowngradeToFreemiumTakesEffectAndStopsTheGatewayCharge() {
            var s = subscription(business, SubscriptionStatus.ACTIVE, ended.minus(30, ChronoUnit.DAYS), ended);
            s.setScheduledPlan(freemium);
            s.setPaystackSubscriptionCode("SUB_1");
            s.setPaystackEmailToken("enc-token");

            service.processEndedPeriod(s, NOW);

            assertThat(s.getPlan()).isEqualTo(freemium);
            assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(s.getScheduledPlan()).isNull();
            assertThat(s.getPaystackSubscriptionCode()).isNull();
            verify(paystackGatewayService).disableSubscription("SUB_1", "plain-enc-token");
        }

        @Test
        void scheduledDowngradeToCheaperPaidPlanAwaitsPayment() {
            var s = subscription(business, SubscriptionStatus.ACTIVE, ended.minus(30, ChronoUnit.DAYS), ended);
            s.setScheduledPlan(basic);

            service.processEndedPeriod(s, NOW);

            assertThat(s.getPlan()).isEqualTo(basic);
            assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
            assertThat(s.getPastDueSince()).isEqualTo(NOW);
            assertThat(s.getScheduledPlan()).isNull();
        }

        @Test
        void cancelledPlanEndsOnFreemium() {
            var s = subscription(business, SubscriptionStatus.ACTIVE, ended.minus(30, ChronoUnit.DAYS), ended);
            s.setAutoRenew(false);

            service.processEndedPeriod(s, NOW);

            assertThat(s.getPlan()).isEqualTo(freemium);
            assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        }

        @Test
        void waitsForTheGatewaysOwnRenewalCharge() {
            var s = subscription(business, SubscriptionStatus.ACTIVE, ended.minus(30, ChronoUnit.DAYS), ended);
            s.setPaystackSubscriptionCode("SUB_1");

            assertThat(service.processEndedPeriod(s, NOW)).isFalse();
            assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        }

        @Test
        void missingGatewayRenewalBecomesPastDueAfterTheWait() {
            Instant longAgo = NOW.minus(2, ChronoUnit.DAYS);
            var s = subscription(business, SubscriptionStatus.ACTIVE, longAgo.minus(30, ChronoUnit.DAYS), longAgo);
            s.setPaystackSubscriptionCode("SUB_1");

            assertThat(service.processEndedPeriod(s, NOW)).isTrue();
            assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
            assertThat(s.getPlan()).isEqualTo(business);
        }

        @Test
        void oneOffPaidPlanWithoutRecurringChargeBecomesPastDue() {
            var s = subscription(business, SubscriptionStatus.ACTIVE, ended.minus(30, ChronoUnit.DAYS), ended);

            service.processEndedPeriod(s, NOW);

            assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
            verify(notificationService).notifyOrgAdmins(any(), any(), anyString(), anyString(), any(), anyString());
        }
    }

    @Test
    @DisplayName("sweep saves changed subscriptions and survives one bad row")
    void sweepSurvivesABadRow() {
        Instant ended = NOW.minus(1, ChronoUnit.HOURS);
        var broken = subscription(business, SubscriptionStatus.ACTIVE, ended.minus(30, ChronoUnit.DAYS), ended);
        broken.setScheduledPlan(freemium);
        when(planRepository.findByCodeAndDeletedAtIsNull("FREEMIUM"))
                .thenReturn(Optional.empty())                // first call: misconfiguration
                .thenReturn(Optional.of(freemium));
        var healthy = subscription(basic, SubscriptionStatus.ACTIVE, ended.minus(30, ChronoUnit.DAYS), ended);
        healthy.setAutoRenew(false);
        when(subscriptionRepository.findByStatusAndCurrentPeriodEndBeforeAndDeletedAtIsNull(
                SubscriptionStatus.ACTIVE, NOW)).thenReturn(List.of(broken, healthy));

        int changed = service.processEndedPeriods();

        assertThat(changed).isEqualTo(1);
        verify(subscriptionRepository, times(1)).save(healthy);
        verify(subscriptionRepository, never()).save(broken);
    }
}
