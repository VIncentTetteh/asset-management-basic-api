package com.assetiq.db;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that ShedLock actually serialises a job across instances.
 *
 * <p>The rest of the suite cannot show this. Tests run in one JVM with one scheduler and,
 * on the {@code test} profile, an always-granting {@link LockProvider} — so a passing suite
 * says nothing about whether two replicas would both run the monthly depreciation batch.
 * The claim being made in production is specifically about <em>separate processes sharing
 * one database</em>, so it is verified the only way that means anything: a real PostgreSQL,
 * the real migration chain, and two independent {@link JdbcTemplateLockProvider} instances
 * standing in for two replicas.
 *
 * <p>Modelled on {@code FlywayMigrationTest} — plain JDBC, no Spring context — because the
 * property under test is a database property, not a wiring one.
 */
@Testcontainers
@DisplayName("ShedLock serialises jobs across instances")
class ShedLockDistributedLockingTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("shedlock_test")
                    .withUsername("postgres")
                    .withPassword("postgres");

    private static DataSource dataSource;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUsername(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        dataSource = ds;
    }

    @Test
    @DisplayName("V29 creates the shedlock table the JDBC provider requires")
    void shedlockTableExists() {
        Integer count = new JdbcTemplate(dataSource).queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'shedlock'",
                Integer.class);

        // The provider fails at runtime, not at startup, if this table is missing — which
        // would mean jobs silently running unlocked on every replica.
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("only one of two instances acquires the same lock")
    void secondInstanceIsRefusedTheLock() {
        LockProvider instanceA = newProvider();
        LockProvider instanceB = newProvider();
        LockConfiguration job = jobLock("monthlyDepreciation");

        Optional<SimpleLock> first = instanceA.lock(job);
        Optional<SimpleLock> second = instanceB.lock(job);

        assertThat(first).describedAs("the first instance must get the lock").isPresent();
        assertThat(second)
                .describedAs("the second instance must be refused while the first holds it — "
                        + "this is what stops depreciation being posted twice")
                .isEmpty();
    }

    @Test
    @DisplayName("a different job is unaffected by another job's lock")
    void locksAreScopedByJobName() {
        LockProvider instanceA = newProvider();
        LockProvider instanceB = newProvider();

        Optional<SimpleLock> dunning = instanceA.lock(jobLock("subscriptionDunning"));
        Optional<SimpleLock> purge = instanceB.lock(jobLock("accountPurge"));

        // Guards against a misconfiguration where every job shares one row and the first
        // job to run each night suppresses all the others.
        assertThat(dunning).isPresent();
        assertThat(purge).isPresent();
    }

    @Test
    @DisplayName("under concurrent contention exactly one instance runs the job")
    void exactlyOneInstanceRunsUnderContention() throws Exception {
        int instances = 8;
        LockConfiguration job = jobLock("contendedJob");

        // Each thread is a separate "replica" with its own provider, all racing the same
        // tick — the real-world shape when N pods share a cron schedule.
        List<LockProvider> providers = new ArrayList<>();
        for (int i = 0; i < instances; i++) {
            providers.add(newProvider());
        }

        AtomicInteger executions = new AtomicInteger();
        CountDownLatch startLine = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(instances);
        ExecutorService pool = Executors.newFixedThreadPool(instances);

        try {
            for (LockProvider provider : providers) {
                pool.submit(() -> {
                    try {
                        startLine.await();
                        provider.lock(job).ifPresent(lock -> executions.incrementAndGet());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finished.countDown();
                    }
                });
            }

            startLine.countDown();
            assertThat(finished.await(30, TimeUnit.SECONDS))
                    .describedAs("all simulated instances should finish their attempt")
                    .isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(executions.get())
                .describedAs("exactly one of %d concurrent instances may run the job", instances)
                .isEqualTo(1);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** A provider configured exactly as {@code ShedLockConfig} configures the real one. */
    private static LockProvider newProvider() {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .withTableName("shedlock")
                        .usingDbTime()
                        .build());
    }

    /**
     * lockAtLeastFor is held deliberately long so the lock is still held when the second
     * instance attempts it — otherwise the test would race the clock rather than assert
     * mutual exclusion.
     */
    private static LockConfiguration jobLock(String name) {
        return new LockConfiguration(Instant.now(), name, Duration.ofMinutes(10), Duration.ofMinutes(5));
    }
}
