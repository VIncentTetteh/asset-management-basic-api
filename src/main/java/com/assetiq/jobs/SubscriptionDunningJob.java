package com.assetiq.jobs;

import com.assetiq.enums.NotificationType;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Acts on subscriptions that have fallen behind on payment.
 *
 * <p>This closes a revenue hole rather than adding a feature. Paystack's
 * {@code invoice.payment_failed} webhook has always flipped a subscription to
 * {@link SubscriptionStatus#PAST_DUE}, but nothing in the codebase ever read that
 * status — a customer whose card expired kept full paid access indefinitely, was
 * never told, and never showed up anywhere an operator would look.
 *
 * <p>The sequence, counted from {@code pastDueSince}:
 * <ol>
 *   <li>reminder emails on each configured day (default 1, 3, 7),</li>
 *   <li>at the end of the grace window (default 14 days) the tenant is moved to the
 *       Freemium plan.</li>
 * </ol>
 *
 * <p><b>Downgrade, not lockout.</b> Moving the tenant to Freemium reuses the quota
 * enforcement that already exists in {@code UsageLimitService}: a tenant over the free
 * caps keeps every asset they own and can still read and export everything, but cannot
 * create more until they pay. Nothing is deleted and nobody is locked out of their own
 * records — which is both the decent behaviour and the one least likely to produce a
 * chargeback or a data-protection complaint.
 *
 * <p>Runs daily at 09:00 UTC, an hour after the end-of-life scan so the two do not
 * contend for the mail pool.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionDunningJob {

    private final OrganisationSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    /** Days after going past due on which a reminder is sent. */
    @Value("${app.billing.dunning.reminder-days:1,3,7}")
    private List<Integer> reminderDays;

    /** Days of full access after going past due, before the downgrade. */
    @Value("${app.billing.dunning.grace-days:14}")
    private int graceDays;

    @Value("${app.email.base-url:http://localhost:3000}")
    private String emailBaseUrl;

    @Scheduled(cron = "0 0 9 * * *", zone = "UTC")
    public void run() {
        List<OrganisationSubscription> pastDue =
                subscriptionRepository.findByStatusAndDeletedAtIsNull(SubscriptionStatus.PAST_DUE);

        if (pastDue.isEmpty()) {
            log.debug("[DUNNING] No past-due subscriptions");
            return;
        }

        log.info("[DUNNING] Processing {} past-due subscription(s)", pastDue.size());
        int downgraded = 0;
        int reminded = 0;

        for (OrganisationSubscription subscription : pastDue) {
            try {
                if (subscription.getPastDueSince() == null) {
                    // Went past due before V27, or the webhook wrote the status without a
                    // clock. Start counting today rather than downgrading immediately.
                    subscription.setPastDueSince(Instant.now());
                    subscriptionRepository.save(subscription);
                    continue;
                }

                long daysPastDue = Duration.between(subscription.getPastDueSince(), Instant.now()).toDays();

                if (daysPastDue >= graceDays) {
                    downgradeToFreemium(subscription, daysPastDue);
                    downgraded++;
                } else if (reminderDays.contains((int) daysPastDue)) {
                    sendReminder(subscription, daysPastDue);
                    reminded++;
                }
            } catch (Exception e) {
                // One tenant's bad data must never stop the sweep for everyone else.
                log.error("[DUNNING] Failed processing subscription {}", subscription.getId(), e);
            }
        }

        log.info("[DUNNING] Complete — {} reminded, {} downgraded", reminded, downgraded);
    }

    private void sendReminder(OrganisationSubscription subscription, long daysPastDue) {
        Organisation org = subscription.getOrganisation();
        long daysRemaining = graceDays - daysPastDue;

        String title = "Payment failed — action required";
        String message = String.format(
                "We couldn't process your payment for the %s plan. "
                        + "Update your billing details within %d day%s to keep your current plan.",
                planName(subscription), daysRemaining, daysRemaining == 1 ? "" : "s");

        notificationService.notifyOrgAdmins(org, NotificationType.SYSTEM,
                title, message, subscription.getId(), "/billing");

        Map<String, Object> model = new HashMap<>();
        model.put("organisationName", org.getName());
        model.put("planName", planName(subscription));
        model.put("daysRemaining", daysRemaining);
        model.put("billingUrl", emailBaseUrl.replaceAll("/+$", "") + "/billing");

        for (User admin : orgAdmins(org)) {
            model.put("firstName", admin.getFirstName());
            emailService.sendTemplate(admin.getEmail(), title, "email/billing-past-due", model);
        }

        log.info("[DUNNING] Reminder sent for org {} — day {} of {}", org.getId(), daysPastDue, graceDays);
    }

    private void downgradeToFreemium(OrganisationSubscription subscription, long daysPastDue) {
        Organisation org = subscription.getOrganisation();
        String previousPlan = planName(subscription);

        SubscriptionPlan freemium = subscriptionPlanRepository.findByCodeAndDeletedAtIsNull("FREEMIUM")
                .orElse(null);
        if (freemium == null) {
            // Seeded by BillingPlanSeeder at startup; if it is genuinely missing, leaving
            // the tenant on their paid plan is far safer than guessing a replacement.
            log.error("[DUNNING] FREEMIUM plan missing — cannot downgrade org {}", org.getId());
            return;
        }

        subscription.setPlan(freemium);
        // ACTIVE on Freemium, not EXPIRED: they are now a legitimate free-tier tenant.
        // Leaving them PAST_DUE would re-enter this sweep forever and keep emailing them.
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setAutoRenew(false);
        subscription.setPastDueSince(null);
        subscriptionRepository.save(subscription);

        String title = "Your plan has been changed to Freemium";
        String message = String.format(
                "After %d days without a successful payment, your %s plan was changed to Freemium. "
                        + "Your data is safe — resubscribe any time to restore your previous limits.",
                daysPastDue, previousPlan);

        notificationService.notifyOrgAdmins(org, NotificationType.SYSTEM,
                title, message, subscription.getId(), "/billing");

        Map<String, Object> model = new HashMap<>();
        model.put("organisationName", org.getName());
        model.put("previousPlanName", previousPlan);
        model.put("daysPastDue", daysPastDue);
        model.put("billingUrl", emailBaseUrl.replaceAll("/+$", "") + "/billing");

        for (User admin : orgAdmins(org)) {
            model.put("firstName", admin.getFirstName());
            emailService.sendTemplate(admin.getEmail(), title, "email/billing-downgraded", model);
        }

        log.warn("[DUNNING] Org {} downgraded from {} to FREEMIUM after {} days past due",
                org.getId(), previousPlan, daysPastDue);
    }

    private List<User> orgAdmins(Organisation org) {
        return userRepository.findByOrganisationAndRole_NameContainingIgnoreCaseAndDeletedAtIsNull(org, "ADMIN");
    }

    private static String planName(OrganisationSubscription subscription) {
        return subscription.getPlan() == null ? "current" : subscription.getPlan().getName();
    }
}
