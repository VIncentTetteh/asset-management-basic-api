package com.assetiq.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Distributed locking for {@code @Scheduled} jobs.
 *
 * <h3>Why</h3>
 * Spring's scheduler is per-JVM. Every replica fires every cron independently, so
 * running two instances silently doubles every job. For this application that is a
 * correctness problem, not a performance one: {@code DepreciationServiceImpl}
 * posts financial entries, {@code SubscriptionDunningJob} emails paying customers
 * and downgrades their plans, {@code AccountPurgeJob} hard-deletes tenants, and
 * {@code WebhookDeliveryRetryScheduler} re-delivers to customer endpoints. Until
 * this existed the application could only ever be run on a single replica — which
 * also meant no rolling deploys and no HA.
 *
 * <h3>Why JDBC rather than Redis</h3>
 * ShedLock offers a Redis provider, and Redis is already a dependency here. But
 * {@code RedisRateLimiter} and {@code JwtBlacklist} both deliberately <em>fail
 * open</em> when Redis is unavailable, which is right for availability and wrong
 * for a lock: a lock that fails open is not a lock. The database is the one store
 * this application cannot run without, so anchoring correctness there means a job
 * either holds the lock or does not run at all.
 *
 * <h3>lockAtMostFor</h3>
 * The default ceiling below is the backstop for an instance that dies mid-job
 * without releasing its lock — the row would otherwise block that job forever.
 * It must exceed the longest plausible runtime of any job; jobs that can run
 * longer set their own value on {@code @SchedulerLock}. Note this is a safety
 * valve, not a timeout: it does not interrupt a running job.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class ShedLockConfig {

    /**
     * Backed by the {@code shedlock} table created in {@code V29__shedlock.sql}.
     *
     * <p>{@code usingDbTime()} makes every instance take its timestamps from the
     * database rather than from its own clock, so a node with drifted time cannot
     * expire a lock another node still holds.
     *
     * <p>Excluded from the {@code test} profile, which runs on H2 with Flyway
     * disabled and therefore has no {@code shedlock} table;
     * {@code TestShedLockConfig} supplies an in-JVM provider there. Real locking
     * behaviour is proven against Postgres in {@code ShedLockDistributedLockingTest}
     * — an H2-backed assertion would prove nothing about two separate JVMs.
     */
    @Bean
    @Profile("!test")
    public LockProvider lockProvider(DataSource dataSource) {
        // usingDbTime() and withTimeZone() are mutually exclusive — setting both throws
        // at startup. usingDbTime() is the one that matters: it takes every timestamp
        // from the database, which is both the single source of truth and immune to
        // clock drift between replicas.
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .withTableName("shedlock")
                        .usingDbTime()
                        .build());
    }
}
