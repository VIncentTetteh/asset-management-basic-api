package com.assetiq.jobs;

import com.assetiq.services.AccountLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Destroys closed accounts once their retention window has elapsed.
 *
 * <p>The counterpart to the soft delete in {@link AccountLifecycleService}: without
 * this, "delete my account" would mean "hide my account", leaving personal data on
 * file indefinitely and making the data-protection position that AssetIQ sells to its
 * own customers untrue of AssetIQ.
 *
 * <p>Runs daily at 03:00 UTC — deliberately in the quiet window, since a purge issues
 * wide cascading deletes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountPurgeJob {

    private final AccountLifecycleService accountLifecycleService;

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    @SchedulerLock(name = "accountPurge", lockAtMostFor = "PT1H", lockAtLeastFor = "PT10M")
    public void run() {
        int purged = accountLifecycleService.purgeExpiredAccounts();
        if (purged > 0) {
            log.warn("[ACCOUNT-PURGE] Permanently deleted {} organisation(s)", purged);
        } else {
            log.debug("[ACCOUNT-PURGE] Nothing due for purge");
        }
    }
}
