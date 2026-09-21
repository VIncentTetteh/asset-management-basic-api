package com.assetiq.jobs;

import com.assetiq.services.impl.SubscriptionLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ends paid periods. Hourly, so a cancelled or downgraded plan changes within an hour
 * of its period end rather than up to a day later; the sweep is an indexed query over
 * ACTIVE subscriptions whose period has ended, so an idle run costs one query.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPeriodEndJob {

    private final SubscriptionLifecycleService lifecycleService;

    @Scheduled(cron = "0 15 * * * *", zone = "UTC")
    @SchedulerLock(name = "subscriptionPeriodEnd", lockAtMostFor = "PT20M", lockAtLeastFor = "PT1M")
    public void run() {
        int changed = lifecycleService.processEndedPeriods();
        if (changed > 0) {
            log.info("[BILLING] Period-end sweep changed {} subscription(s)", changed);
        }
    }
}
