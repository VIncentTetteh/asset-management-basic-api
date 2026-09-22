package com.assetiq.jobs;

import com.assetiq.repositories.ContractRepository;
import com.assetiq.repositories.LeaseRecordRepository;
import com.assetiq.repositories.SoftwareLicenseRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ExpiryStatusJobTest {

    private final ContractRepository contracts = mock(ContractRepository.class);
    private final SoftwareLicenseRepository licenses = mock(SoftwareLicenseRepository.class);
    private final LeaseRecordRepository leases = mock(LeaseRecordRepository.class);

    @Test
    void expiresEachKindAsOfTodayInUtc() {
        Instant now = Instant.parse("2026-09-22T00:15:00Z");
        new ExpiryStatusJob(contracts, licenses, leases, Clock.fixed(now, ZoneOffset.UTC)).run();

        LocalDate today = LocalDate.of(2026, 9, 22);
        verify(contracts).expirePastEndDate(today, now);
        verify(licenses).expirePastExpiryDate(today, now);
        verify(leases).expirePastEndDate(today, now);
    }

    @Test
    void runsDailyUnderAShedLock() throws Exception {
        var run = ExpiryStatusJob.class.getMethod("run");
        assertThat(run.getAnnotation(Scheduled.class).cron()).isEqualTo("0 15 0 * * *");
        assertThat(run.getAnnotation(SchedulerLock.class).name()).isEqualTo("expiryStatus");
    }
}
