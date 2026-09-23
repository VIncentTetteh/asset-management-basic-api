package com.assetiq.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
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
 * V61: one live insight per asset and type.
 *
 * <p>The service upserts by deleting the unresolved insight for an asset+type
 * and inserting a fresh one. Two refreshes at once — the nightly job and someone
 * pressing Regenerate — could both delete and both insert. Only the database can
 * hold that invariant, so this runs the real migration chain rather than H2.
 */
@Testcontainers
@DisplayName("V61 - one live predictive insight per asset and type")
class PredictiveInsightUniquenessMigrationTest {

    @Container
    @SuppressWarnings("resource")
    final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("v61")
            .withUsername("postgres")
            .withPassword("postgres");

    private Flyway flyway(String target) {
        var config = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static final UUID ORG   = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID ASSET = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private static void seedOrgAndAsset(Statement st) throws SQLException {
        st.execute("INSERT INTO organisation (id, name) VALUES ('" + ORG + "', 'Insight Org')");
        st.execute("INSERT INTO asset (id, organisation_id, name) VALUES ('" + ASSET + "', '" + ORG + "', 'Laptop')");
    }

    private static void insight(Statement st, String type, boolean resolved, boolean deleted, String createdAt)
            throws SQLException {
        st.execute("INSERT INTO predictive_insight (id, organisation_id, asset_id, insight_type, description,"
                + " resolved, created_at, deleted_at) VALUES ('" + UUID.randomUUID() + "', '" + ORG + "', '"
                + ASSET + "', '" + type + "', 'derived', " + resolved + ", TIMESTAMPTZ '" + createdAt + "', "
                + (deleted ? "now()" : "NULL") + ")");
    }

    private int liveCount(Statement st, String type) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT count(*) FROM predictive_insight WHERE insight_type = '"
                + type + "' AND resolved = false AND deleted_at IS NULL")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    @DisplayName("existing duplicate derived rows are collapsed to the newest")
    void duplicatesAreDeduped() throws Exception {
        flyway("60").migrate();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            seedOrgAndAsset(st);
            insight(st, "MAINTENANCE_DUE", false, false, "2026-01-01 00:00:00+00");
            insight(st, "MAINTENANCE_DUE", false, false, "2026-01-02 00:00:00+00");
            insight(st, "MAINTENANCE_DUE", false, false, "2026-01-03 00:00:00+00");
            assertThat(liveCount(st, "MAINTENANCE_DUE")).isEqualTo(3);
        }

        flyway(null).migrate();

        try (Connection c = connect(); Statement st = c.createStatement()) {
            assertThat(liveCount(st, "MAINTENANCE_DUE")).isEqualTo(1);
            try (ResultSet rs = st.executeQuery(
                    "SELECT created_at FROM predictive_insight WHERE insight_type = 'MAINTENANCE_DUE'")) {
                rs.next();
                assertThat(rs.getString(1)).startsWith("2026-01-03");
            }
        }
    }

    @Test
    @DisplayName("a second live insight for the same asset and type is refused")
    void secondLiveInsightIsRefused() throws Exception {
        flyway(null).migrate();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            seedOrgAndAsset(st);
            insight(st, "WARRANTY_EXPIRY", false, false, "2026-02-01 00:00:00+00");

            assertThatThrownBy(() -> insight(st, "WARRANTY_EXPIRY", false, false, "2026-02-02 00:00:00+00"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uq_predictive_insight_live");
        }
    }

    @Test
    @DisplayName("history still accumulates: resolved and soft-deleted rows are exempt")
    void resolvedAndDeletedRowsAreExempt() throws Exception {
        flyway(null).migrate();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            seedOrgAndAsset(st);
            // An asset can go overdue, be resolved, and go overdue again.
            insight(st, "FAILURE_RISK", true,  false, "2026-03-01 00:00:00+00");
            insight(st, "FAILURE_RISK", true,  false, "2026-03-02 00:00:00+00");
            insight(st, "FAILURE_RISK", false, true,  "2026-03-03 00:00:00+00");
            insight(st, "FAILURE_RISK", false, false, "2026-03-04 00:00:00+00");

            assertThat(liveCount(st, "FAILURE_RISK")).isEqualTo(1);
            try (ResultSet rs = st.executeQuery(
                    "SELECT count(*) FROM predictive_insight WHERE insight_type = 'FAILURE_RISK'")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(4);
            }
        }
    }

    @Test
    @DisplayName("a different type for the same asset is fine")
    void differentTypesCoexist() throws Exception {
        flyway(null).migrate();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            seedOrgAndAsset(st);
            insight(st, "ASSET_AGING", false, false, "2026-04-01 00:00:00+00");
            insight(st, "UNDERUTILIZED", false, false, "2026-04-01 00:00:00+00");

            assertThat(liveCount(st, "ASSET_AGING")).isEqualTo(1);
            assertThat(liveCount(st, "UNDERUTILIZED")).isEqualTo(1);
        }
    }
}
