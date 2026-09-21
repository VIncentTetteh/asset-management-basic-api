package com.assetiq.services.impl;

import com.assetiq.enums.BillingInterval;
import com.assetiq.enums.BillingPlanTier;
import com.assetiq.enums.NotificationType;
import com.assetiq.enums.SubscriptionStatus;
import com.assetiq.models.Organisation;
import com.assetiq.models.OrganisationSubscription;
import com.assetiq.models.SubscriptionPlan;
import com.assetiq.repositories.OrganisationSubscriptionRepository;
import com.assetiq.repositories.SubscriptionPlanRepository;
import com.assetiq.security.SecretCryptoService;
import com.assetiq.services.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Owns how a subscription moves through time: what a paid period buys, what happens
 * when it ends, and how a plan change is classified.
 *
 * <p>Before this existed nothing ever read {@code currentPeriodEnd}: a single payment
 * bought a paid plan forever, a cancelled plan never ended, and a downgrade the UI
 * promised "at period end" never happened. The rules here are deliberately simple and
 * customer-favourable:
 * <ul>
 *   <li><b>Upgrades apply immediately</b>, and the unused value of the old period is
 *       credited as extra time on the new plan, so nobody pays twice for the same days.</li>
 *   <li><b>Downgrades wait for the period end</b>: the customer keeps what they paid for.</li>
 *   <li><b>An ended paid period</b> either renews (gateway charge, handled by webhook),
 *       moves to Freemium (cancelled or downgraded to free), or becomes PAST_DUE and
 *       enters the dunning grace window. Nothing is ever deleted.</li>
 * </ul>
 */
@Service
@Slf4j
@Transactional
public class SubscriptionLifecycleService {

    static final String FREEMIUM_CODE = "FREEMIUM";
    private static final int FREEMIUM_PERIOD_DAYS = 365;
    private static final int MONTHLY_PERIOD_DAYS = 30;
    private static final int ANNUAL_PERIOD_DAYS = 365;

    /** How direction of a requested plan change is decided. */
    public enum ChangeDirection { UPGRADE, DOWNGRADE, SAME }

    private final OrganisationSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final PaystackGatewayService paystackGatewayService;
    private final SecretCryptoService secretCryptoService;
    private final NotificationService notificationService;

    /**
     * How long after a period ends we wait for the gateway's own recurring charge
     * before treating the renewal as failed. Paystack charges on the renewal date but
     * the webhook can lag; without this window every renewal would briefly flag
     * PAST_DUE.
     */
    @Value("${app.billing.renewal-wait-hours:24}")
    private long renewalWaitHours = 24;

    private Clock clock = Clock.systemUTC();

    public SubscriptionLifecycleService(OrganisationSubscriptionRepository subscriptionRepository,
                                        SubscriptionPlanRepository planRepository,
                                        PaystackGatewayService paystackGatewayService,
                                        SecretCryptoService secretCryptoService,
                                        NotificationService notificationService) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.paystackGatewayService = paystackGatewayService;
        this.secretCryptoService = secretCryptoService;
        this.notificationService = notificationService;
    }

    // ── Classification ───────────────────────────────────────────────────────

    /**
     * Upgrade when the target costs more per month, or when the tenant is not currently
     * entitled to a paid plan at all (free, or its paid period has lapsed). Monthly and
     * annual prices are compared per month, so monthly → annual on the same tier is an
     * upgrade (paid now) and annual → monthly is a downgrade (at period end).
     */
    public ChangeDirection classify(OrganisationSubscription subscription, SubscriptionPlan target) {
        SubscriptionPlan current = subscription.getPlan();
        if (current != null && Objects.equals(current.getId(), target.getId())) {
            return ChangeDirection.SAME;
        }
        if (current == null || isFree(current) || subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            return isFree(target) ? ChangeDirection.DOWNGRADE : ChangeDirection.UPGRADE;
        }
        return monthlyEquivalent(target) > monthlyEquivalent(current)
                ? ChangeDirection.UPGRADE
                : ChangeDirection.DOWNGRADE;
    }

    public static boolean isFree(SubscriptionPlan plan) {
        return plan.getTier() == BillingPlanTier.FREEMIUM
                || plan.getAmountMinor() == null
                || plan.getAmountMinor() <= 0;
    }

    // ── Paid periods ─────────────────────────────────────────────────────────

    /**
     * Applies a successful payment for {@code paidPlan}.
     *
     * <p>Same plan: a renewal. The period continues from the current end when paid
     * early, or starts now when the old period has already lapsed. Different plan: an
     * upgrade that starts now, with the unused value of the old period added as extra
     * time. Either way any pending downgrade, cancellation or dunning clock is cleared,
     * because the customer has just paid for this plan.
     */
    public void applyPaidPeriod(OrganisationSubscription subscription, SubscriptionPlan paidPlan) {
        Instant now = Instant.now(clock);
        boolean renewal = subscription.getPlan() != null
                && Objects.equals(subscription.getPlan().getId(), paidPlan.getId());

        if (renewal && subscription.getCurrentPeriodEnd() != null
                && subscription.getCurrentPeriodEnd().isAfter(now)
                && subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            subscription.setCurrentPeriodEnd(periodEnd(subscription.getCurrentPeriodEnd(), paidPlan));
        } else {
            Duration credit = renewal ? Duration.ZERO : unusedCredit(subscription, paidPlan, now);
            subscription.setCurrentPeriodStart(now);
            subscription.setCurrentPeriodEnd(periodEnd(now, paidPlan).plus(credit));
        }

        subscription.setPlan(paidPlan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setPastDueSince(null);
        subscription.setCanceledAt(null);
        subscription.setScheduledPlan(null);
        subscription.setScheduledChangeAt(null);
        subscription.setAutoRenew(true);
        subscription.setNextBillingAt(subscription.getCurrentPeriodEnd());
    }

    /**
     * Unused value of the current paid period, re-expressed as time on {@code newPlan}.
     *
     * <p>Only credited when both prices are in the same currency; converting a refund
     * value across currencies would need a dated rate this path does not have, and
     * guessing would misprice the credit.
     */
    Duration unusedCredit(OrganisationSubscription subscription, SubscriptionPlan newPlan, Instant now) {
        SubscriptionPlan old = subscription.getPlan();
        Instant start = subscription.getCurrentPeriodStart();
        Instant end = subscription.getCurrentPeriodEnd();
        if (old == null || isFree(old) || isFree(newPlan) || start == null || end == null
                || subscription.getStatus() != SubscriptionStatus.ACTIVE
                || !end.isAfter(now) || !end.isAfter(start)
                || !Objects.equals(old.getCurrency(), newPlan.getCurrency())) {
            return Duration.ZERO;
        }
        double remainingFraction = (double) Duration.between(now, end).getSeconds()
                / Duration.between(start, end).getSeconds();
        double unusedMinor = old.getAmountMinor() * remainingFraction;
        long newPeriodSeconds = Duration.ofDays(periodDays(newPlan)).getSeconds();
        long creditSeconds = (long) (unusedMinor / newPlan.getAmountMinor() * newPeriodSeconds);
        return Duration.ofSeconds(Math.max(0, creditSeconds));
    }

    // ── Period end ───────────────────────────────────────────────────────────

    /** Runs every ACTIVE subscription whose paid period has ended through its transition. */
    public int processEndedPeriods() {
        Instant now = Instant.now(clock);
        List<OrganisationSubscription> ended = subscriptionRepository
                .findByStatusAndCurrentPeriodEndBeforeAndDeletedAtIsNull(SubscriptionStatus.ACTIVE, now);
        int changed = 0;
        for (OrganisationSubscription subscription : ended) {
            try {
                if (processEndedPeriod(subscription, now)) {
                    subscriptionRepository.save(subscription);
                    changed++;
                }
            } catch (RuntimeException e) {
                // One tenant's bad data must never stop the sweep for everyone else.
                log.error("[BILLING] Period-end processing failed for subscription {}", subscription.getId(), e);
            }
        }
        return changed;
    }

    /** @return true when the subscription was changed and must be saved */
    boolean processEndedPeriod(OrganisationSubscription subscription, Instant now) {
        SubscriptionPlan plan = subscription.getPlan();
        if (plan == null || isFree(plan)) {
            // Free and custom (sales-managed) plans simply roll over.
            subscription.setCurrentPeriodStart(now);
            subscription.setCurrentPeriodEnd(now.plus(FREEMIUM_PERIOD_DAYS, ChronoUnit.DAYS));
            return true;
        }
        SubscriptionPlan scheduled = subscription.getScheduledPlan();
        if (scheduled != null) {
            if (isFree(scheduled)) {
                moveToFreemium(subscription, now, "Your scheduled change to Freemium is now in effect.");
            } else {
                startAwaitingPayment(subscription, scheduled, now);
            }
            return true;
        }
        if (!Boolean.TRUE.equals(subscription.getAutoRenew())) {
            moveToFreemium(subscription, now,
                    "Your " + plan.getName() + " plan was cancelled and has ended. You are now on Freemium.");
            return true;
        }
        boolean gatewayRenews = subscription.getPaystackSubscriptionCode() != null
                && !subscription.getPaystackSubscriptionCode().isBlank();
        if (gatewayRenews && subscription.getCurrentPeriodEnd()
                .plus(renewalWaitHours, ChronoUnit.HOURS).isAfter(now)) {
            return false; // the gateway's own renewal charge is still expected
        }
        startAwaitingPayment(subscription, plan, now);
        return true;
    }

    /**
     * The period ended without a renewal charge (or the tenant scheduled a cheaper paid
     * plan). PAST_DUE starts the dunning grace window: full access continues, reminders
     * go out, and the tenant moves to Freemium only if nobody pays.
     */
    private void startAwaitingPayment(OrganisationSubscription subscription, SubscriptionPlan plan, Instant now) {
        subscription.setPlan(plan);
        subscription.setScheduledPlan(null);
        subscription.setScheduledChangeAt(null);
        subscription.setStatus(SubscriptionStatus.PAST_DUE);
        if (subscription.getPastDueSince() == null) {
            subscription.setPastDueSince(now);
        }
        subscription.setNextBillingAt(now);
        notifyAdmins(subscription.getOrganisation(), "Renewal payment due",
                "Your " + plan.getName() + " period has ended. Pay from the Billing page to keep your "
                        + "plan; your current limits stay in place during the grace period.");
    }

    /** Moves the tenant to Freemium. Used by period-end processing and by dunning. */
    public void moveToFreemium(OrganisationSubscription subscription, Instant now, String reason) {
        SubscriptionPlan freemium = planRepository.findByCodeAndDeletedAtIsNull(FREEMIUM_CODE)
                .orElseThrow(() -> new IllegalStateException("FREEMIUM plan is not configured"));
        disableGatewaySubscription(subscription);
        subscription.setPlan(freemium);
        // ACTIVE on Freemium: a legitimate free-tier tenant, not a lapsed one.
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setAutoRenew(false);
        subscription.setPastDueSince(null);
        subscription.setScheduledPlan(null);
        subscription.setScheduledChangeAt(null);
        subscription.setPaystackSubscriptionCode(null);
        subscription.setPaystackEmailToken(null);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(now.plus(FREEMIUM_PERIOD_DAYS, ChronoUnit.DAYS));
        subscription.setNextBillingAt(null);
        notifyAdmins(subscription.getOrganisation(), "Your plan has changed to Freemium", reason);
    }

    /**
     * Stops the gateway's recurring charge. Best effort: a failure is logged, not thrown,
     * because refusing a downgrade or plan switch over a gateway hiccup would leave the
     * tenant stuck. Paystack's own subscription.disable webhook reconciles later.
     */
    public void disableGatewaySubscription(OrganisationSubscription subscription) {
        String code = subscription.getPaystackSubscriptionCode();
        String token = subscription.getPaystackEmailToken();
        if (code == null || code.isBlank() || token == null || token.isBlank()) {
            return;
        }
        try {
            paystackGatewayService.disableSubscription(code, secretCryptoService.decrypt(token));
        } catch (RuntimeException e) {
            log.warn("[BILLING] Could not disable Paystack subscription {} for org {}: {}",
                    code, subscription.getOrganisation() == null ? null : subscription.getOrganisation().getId(),
                    e.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    public Instant periodEnd(Instant start, SubscriptionPlan plan) {
        return start.plus(periodDays(plan), ChronoUnit.DAYS);
    }

    private static int periodDays(SubscriptionPlan plan) {
        return plan.getInterval() == BillingInterval.ANNUALLY ? ANNUAL_PERIOD_DAYS : MONTHLY_PERIOD_DAYS;
    }

    private static double monthlyEquivalent(SubscriptionPlan plan) {
        long amount = plan.getAmountMinor() == null ? 0L : plan.getAmountMinor();
        return plan.getInterval() == BillingInterval.ANNUALLY ? amount / 12.0 : amount;
    }

    private void notifyAdmins(Organisation organisation, String title, String message) {
        if (organisation == null) {
            return;
        }
        try {
            notificationService.notifyOrgAdmins(organisation, NotificationType.SYSTEM, title, message, null, "/billing");
        } catch (RuntimeException e) {
            log.warn("[BILLING] Could not notify admins of org {}: {}", organisation.getId(), e.getMessage());
        }
    }

    /** Test seam. */
    void setClock(Clock clock) {
        this.clock = clock;
    }
}
