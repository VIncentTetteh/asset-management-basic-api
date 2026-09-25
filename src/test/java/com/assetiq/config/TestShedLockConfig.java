package com.assetiq.config;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Optional;

/**
 * Supplies a {@link LockProvider} for the {@code test} profile.
 *
 * <p>{@code ShedLockConfig}'s real provider is JDBC-backed and needs the
 * {@code shedlock} table from {@code V29__shedlock.sql}. The test profile runs on
 * H2 with {@code ddl-auto=create-drop} and Flyway <em>disabled</em>, so migrations
 * never run and that table does not exist. Without a provider here every
 * {@code @SpringBootTest} context would fail to start.
 *
 * <p>This one always grants the lock. That is correct for the suite's purpose: the
 * tests run in a single JVM with one scheduler, so there is no contention to
 * arbitrate, and the existing suites assert what a job <em>does</em>, not whether it
 * was allowed to run. Nothing about real mutual exclusion is claimed here — it is
 * proven against Postgres, with two independent providers, in
 * {@code ShedLockDistributedLockingTest}.
 */
@Configuration
@Profile("test")
public class TestShedLockConfig {

    @Bean
    public LockProvider lockProvider() {
        return new LockProvider() {
            @Override
            public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
                return Optional.of(new SimpleLock() {
                    @Override
                    public void unlock() {
                        // no-op: nothing to release
                    }
                });
            }
        };
    }
}
