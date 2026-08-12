package com.assetiq.jobs;

import com.assetiq.enums.SubscriptionStatus;
import com.assetiq.models.Organisation;
import com.assetiq.models.OrganisationSubscription;
import com.assetiq.models.SubscriptionPlan;
import com.assetiq.models.User;
import com.assetiq.repositories.OrganisationSubscriptionRepository;
import com.assetiq.repositories.SubscriptionPlanRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.services.EmailService;
import com.assetiq.services.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the dunning sweep.
 *
 * <p>Constructor order follows {@code @RequiredArgsConstructor} on the job:
 * subscriptionRepository, subscriptionPlanRepository, userRepository,
 * notificationService, emailService. The {@code @Value} fields are set through
 * {@link ReflectionTestUtils} since no Spring context is involved.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SubscriptionDunningJob")
class SubscriptionDunningJobTest {

    @Mock OrganisationSubscriptionRepository subscriptionRepository;
    @Mock SubscriptionPlanRepository         subscriptionPlanRepository;
    @Mock UserRepository                     userRepository;
    @Mock NotificationService                notificationService;
    @Mock EmailService                       emailService;

    SubscriptionDunningJob job;
    Organisation org;
    SubscriptionPlan businessPlan;
    SubscriptionPlan freemiumPlan;

    @BeforeEach
    void setUp() {
        job = new SubscriptionDunningJob(subscriptionRepository, subscriptionPlanRepository,
                userRepository, notificationService, emailService);
        ReflectionTestUtils.setField(job, "reminderDays", List.of(1, 3, 7));
        ReflectionTestUtils.setField(job, "graceDays", 14);
        ReflectionTestUtils.setField(job, "emailBaseUrl", "https://app.example.com");

        org = new Organisation();
        org.setId(UUID.randomUUID());
        org.setName("Kwabenya Depot Ltd");

        businessPlan = new SubscriptionPlan();
        businessPlan.setId(UUID.randomUUID());
        businessPlan.setCode("BUSINESS");
        businessPlan.setName("Business");

        freemiumPlan = new SubscriptionPlan();
        freemiumPlan.setId(UUID.randomUUID());
        freemiumPlan.setCode("FREEMIUM");
        freemiumPlan.setName("Freemium");

        User admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setFirstName("Ama");
        admin.setEmail("ama@example.com");
        when(userRepository.findByOrganisationAndRole_NameContainingIgnoreCaseAndDeletedAtIsNull(any(), anyString()))
                .thenReturn(List.of(admin));
        when(subscriptionPlanRepository.findByCodeAndDeletedAtIsNull("FREEMIUM"))
                .thenReturn(Optional.of(freemiumPlan));
    }

    @Test
    @DisplayName("sends a reminder on a configured reminder day")
    void remindsOnReminderDay() {
        givenPastDue(daysAgo(3));

        job.run();

        verify(emailService).sendTemplate(eq("ama@example.com"), anyString(),
                eq("email/billing-past-due"), anyMap());
        verify(notificationService).notifyOrgAdmins(eq(org), any(), anyString(), anyString(), any(), eq("/billing"));
    }

    @Test
    @DisplayName("stays quiet on a day that is not a reminder day")
    void silentOnNonReminderDay() {
        givenPastDue(daysAgo(5));

        job.run();

        verify(emailService, never()).sendTemplate(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("downgrades to Freemium once the grace window closes")
    void downgradesAfterGraceWindow() {
        OrganisationSubscription subscription = givenPastDue(daysAgo(14));

        job.run();

        assertThat(subscription.getPlan()).isEqualTo(freemiumPlan);
        // ACTIVE, not PAST_DUE — otherwise the tenant is swept and emailed forever.
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getAutoRenew()).isFalse();
        assertThat(subscription.getPastDueSince())
                .as("the dunning clock must stop once the sequence has run its course")
                .isNull();

        verify(emailService).sendTemplate(eq("ama@example.com"), anyString(),
                eq("email/billing-downgraded"), anyMap());
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    @DisplayName("a missing FREEMIUM plan leaves the tenant on their paid plan")
    void missingFreemiumPlan_doesNotStripTheTenant() {
        when(subscriptionPlanRepository.findByCodeAndDeletedAtIsNull("FREEMIUM"))
                .thenReturn(Optional.empty());
        OrganisationSubscription subscription = givenPastDue(daysAgo(30));

        job.run();

        assertThat(subscription.getPlan())
                .as("failing safe means keeping the plan they paid for, not guessing a replacement")
                .isEqualTo(businessPlan);
        verify(subscriptionRepository, never()).save(subscription);
    }

    @Test
    @DisplayName("a past-due row with no clock starts one instead of downgrading immediately")
    void missingClock_startsTheClock() {
        OrganisationSubscription subscription = givenPastDue(null);

        job.run();

        assertThat(subscription.getPastDueSince())
                .as("rows that predate V27 must enter the sequence, not skip to the end")
                .isNotNull();
        assertThat(subscription.getPlan()).isEqualTo(businessPlan);
        verify(emailService, never()).sendTemplate(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("one broken tenant does not abort the sweep for the others")
    void oneFailure_doesNotStopTheSweep() {
        OrganisationSubscription broken = subscription(daysAgo(3));
        broken.setOrganisation(null); // NPE inside sendReminder
        OrganisationSubscription healthy = subscription(daysAgo(3));

        when(subscriptionRepository.findByStatusAndDeletedAtIsNull(SubscriptionStatus.PAST_DUE))
                .thenReturn(List.of(broken, healthy));

        job.run();

        // The healthy tenant behind the broken one still gets its reminder.
        verify(emailService).sendTemplate(eq("ama@example.com"), anyString(),
                eq("email/billing-past-due"), anyMap());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Instant daysAgo(int days) {
        return Instant.now().minus(Duration.ofDays(days));
    }

    private OrganisationSubscription subscription(Instant pastDueSince) {
        OrganisationSubscription s = new OrganisationSubscription();
        s.setId(UUID.randomUUID());
        s.setOrganisation(org);
        s.setPlan(businessPlan);
        s.setStatus(SubscriptionStatus.PAST_DUE);
        s.setAutoRenew(true);
        s.setPastDueSince(pastDueSince);
        return s;
    }

    private OrganisationSubscription givenPastDue(Instant pastDueSince) {
        OrganisationSubscription s = subscription(pastDueSince);
        when(subscriptionRepository.findByStatusAndDeletedAtIsNull(SubscriptionStatus.PAST_DUE))
                .thenReturn(List.of(s));
        return s;
    }
}
