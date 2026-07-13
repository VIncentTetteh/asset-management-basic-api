package com.assetiq.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the full Flyway migration chain (beforeMigrate + V1..Vn) against a
 * fresh PostgreSQL and asserts the schema objects the application relies on
 * exist. The Spring test profile disables Flyway (ddl-auto=create-drop), so
 * without this test a broken migration would only surface at deploy time.
 */
@Testcontainers
class FlywayMigrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("flyway_fresh")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @Test
    void migrationsApplyCleanlyToFreshDatabase() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();

        int applied = flyway.migrate().migrationsExecuted;
        assertTrue(applied >= 22, "Expected at least 22 migrations, ran " + applied);

        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement st = conn.createStatement()) {

            // V22 employee module objects
            assertTableExists(st, "employee");
            assertTableExists(st, "employee_checklist");
            assertTableExists(st, "employee_checklist_item");
            assertColumnExists(st, "checkout_records", "employee_id");

            // Spot-check core tables from the baseline are intact
            assertTableExists(st, "asset");
            assertTableExists(st, "app_user");
            assertTableExists(st, "organisation");
        }
    }

    private void assertTableExists(Statement st, String table) throws Exception {
        try (ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema = 'public' AND table_name = '" + table + "'")) {
            rs.next();
            assertEquals(1, rs.getInt(1), "Missing table: " + table);
        }
    }

    private void assertColumnExists(Statement st, String table, String column) throws Exception {
        try (ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = 'public' AND table_name = '" + table
                        + "' AND column_name = '" + column + "'")) {
            rs.next();
            assertEquals(1, rs.getInt(1), "Missing column: " + table + "." + column);
        }
    }
}
