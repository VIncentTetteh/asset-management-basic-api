package com.assetiq.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * FlywayRepairConfig — development helper
 *
 * Registers a {@link FlywayMigrationStrategy} that calls {@code flyway.repair()}
 * before {@code flyway.migrate()} on every startup.
 *
 * <p><strong>Why this exists:</strong> Flyway stores a CRC32 checksum for every
 * migration file in the {@code flyway_schema_history} table. If a migration file
 * is modified after being applied (e.g. during development iteration), subsequent
 * startups fail with "Migration checksum mismatch". {@code repair()} resyncs the
 * stored checksums to match the current files, allowing the app to start.</p>
 *
 * <p><strong>Safety:</strong> {@code repair()} only updates checksums and
 * descriptions in the history table — it does NOT roll back or re-apply
 * migrations. The actual schema remains untouched.</p>
 *
 * <p>This bean is active on the {@code default} and {@code dev} profiles.
 * It is excluded from the {@code prod} profile. In production, migration
 * files should never be modified after deployment.</p>
 */
@Configuration
@Profile("!prod")   // never run in production — migration files must not change there
public class FlywayRepairConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayRepairConfig.class);

    /**
     * Returns a migration strategy that repairs checksum mismatches before migrating.
     *
     * <p>Repair is idempotent — if checksums already match, calling it is a no-op.</p>
     */
    @Bean
    public FlywayMigrationStrategy repairThenMigrateStrategy() {
        return flyway -> {
            log.info("[Flyway] Running repair() to resync migration checksums…");
            flyway.repair();
            log.info("[Flyway] Repair complete. Running migrate()…");
            flyway.migrate();
        };
    }
}
