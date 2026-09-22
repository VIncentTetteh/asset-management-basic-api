package com.assetiq.jobs;

import com.assetiq.repositories.ContractRepository;
import com.assetiq.repositories.LeaseRecordRepository;
import com.assetiq.repositories.SoftwareLicenseRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Daily: contracts, software licences and leases whose end (or expiry) date has
 * passed move from ACTIVE (or EXPIRING_SOON) to EXPIRED.
 *
 * <p>Before this job these statuses only changed by hand, so a record read
 * ACTIVE for months after it lapsed. The date is exclusive: a contract ending
 * today is still in force today and expires tomorrow. Auto-renewing records are
 * left alone, since they renew rather than lapse. TERMINATED, DRAFT, RENEWED and
 * PENDING_RENEWAL are never touched. Each update is a single idempotent bulk
 * statement, so a rerun changes nothing.
 */
@Component
public class ExpiryStatusJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiryStatusJob.class);

    private final ContractRepository contractRepository;
    private final SoftwareLicenseRepository licenseRepository;
    private final LeaseRecordRepository leaseRecordRepository;
    private final Clock clock;

    public ExpiryStatusJob(ContractRepository contractRepository,
                           SoftwareLicenseRepository licenseRepository,
                           LeaseRecordRepository leaseRecordRepository) {
        this(contractRepository, licenseRepository, leaseRecordRepository, Clock.systemUTC());
    }

    ExpiryStatusJob(ContractRepository contractRepository,
                    SoftwareLicenseRepository licenseRepository,
                    LeaseRecordRepository leaseRecordRepository,
                    Clock clock) {
        this.contractRepository = contractRepository;
        this.licenseRepository = licenseRepository;
        this.leaseRecordRepository = leaseRecordRepository;
        this.clock = clock;
    }

    /** 00:15 UTC, before the 07:30 lifecycle alerts read the statuses. */
    @Scheduled(cron = "0 15 0 * * *", zone = "UTC")
    @SchedulerLock(name = "expiryStatus", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    @Transactional
    public void run() {
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        int contracts = contractRepository.expirePastEndDate(today, now);
        int licenses = licenseRepository.expirePastExpiryDate(today, now);
        int leases = leaseRecordRepository.expirePastEndDate(today, now);
        log.info("[ExpiryStatus] Expired {} contract(s), {} licence(s), {} lease(s) ending before {}",
                contracts, licenses, leases, today);
    }
}
