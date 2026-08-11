package com.assetiq.jobs;

import com.assetiq.repositories.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Nightly job that purges idempotency records older than {@value #TTL_DAYS} days.
 *
 * Clients retrying after the TTL window are treated as new requests, so it is
 * safe to remove the older entries. This prevents the idempotency_record table
 * from growing unbounded over time.
 *
 * Runs at 02:00 UTC daily (off-peak, before the EOL-alert job at 08:00 UTC).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyCleanupJob {

    static final int TTL_DAYS = 7;

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    @SchedulerLock(name = "idempotencyCleanup", lockAtMostFor = "PT15M", lockAtLeastFor = "PT10M")
    @Transactional
    public void run() {
        Instant cutoff = Instant.now().minus(TTL_DAYS, ChronoUnit.DAYS);
        log.info("[Idempotency-Cleanup] Purging records older than {} days (cutoff={})", TTL_DAYS, cutoff);

        int deleted = idempotencyRecordRepository.deleteByCreatedAtBefore(cutoff);

        log.info("[Idempotency-Cleanup] Deleted {} stale idempotency record(s)", deleted);
    }
}
