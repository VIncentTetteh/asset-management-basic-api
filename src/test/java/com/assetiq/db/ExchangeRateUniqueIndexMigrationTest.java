package com.assetiq.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V51: one live exchange rate per organisation, pair and effective date. Soft-deleted
 * rates never block a new one, and existing live duplicates fail the migration
 * without touching data.
 */
@Testcontainers
class ExchangeRateUniqueIndexMigrationTest {

    @Container
    @SuppressWarnings("resource")
    final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("v51")
            .withUsername("postgres")
            .withPassword("postgres");

    private Flyway flyway(String target) {
        var config = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        if (target != null) config.target(target);
        return config.load();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static UUID organisation(Statement st) throws SQLException {
        UUID org = UUID.randomUUID();
        st.execute("INSERT INTO organisation (id, name) VALUES ('" + org + "', 'Org " + org + "')");
        return org;
    }

    private static void rate(Statement st, UUID org, String date, boolean deleted) throws SQLException {
        st.execute("INSERT INTO exchange_rates (id, organisation_id, base_currency, target_currency, rate,"
                + " effective_date, deleted_at) VALUES ('" + UUID.randomUUID() + "', '" + org + "', 'USD', 'GHS',"
                + " 15.5, DATE '" + date + "', " + (deleted ? "now()" : "NULL") + ")");
    }

    @Test
    void oneLiveRatePerPairAndDate() throws Exception {
        flyway(null).migrate();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            UUID org = organisation(st);
            rate(st, org, "2026-09-01", true);
            rate(st, org, "2026-09-01", false);
            rate(st, org, "2026-09-02", false);

            assertThatThrownBy(() -> rate(st, org, "2026-09-01", false))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uq_exchange_rates_org_pair_date_live")
                    .extracting(e -> ((SQLException) e).getSQLState()).isEqualTo("23505");

            rate(st, organisation(st), "2026-09-01", false);
        }
    }

    @Test
    void existingLiveDuplicatesFailTheMigrationWithoutTouchingData() throws Exception {
        flyway("50").migrate();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            UUID org = organisation(st);
            rate(st, org, "2026-09-01", false);
            rate(st, org, "2026-09-01", false);
        }

        assertThatThrownBy(() -> flyway(null).migrate())
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("live duplicate exchange-rate group(s)");

        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM exchange_rates WHERE deleted_at IS NULL")) {
            rs.next();
            assertThat(rs.getInt(1)).as("no data deleted").isEqualTo(2);
        }
    }
}
