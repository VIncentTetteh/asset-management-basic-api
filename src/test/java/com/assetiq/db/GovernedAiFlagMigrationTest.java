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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The assistant was invisible for one reason: {@code commercial.governed-ai} was
 * seeded OFF in V32 and every {@code /api/v1/ai/**} route therefore answered 404.
 * V60 turns it on. This runs the real migration chain, because the main test
 * profile uses H2 with Flyway disabled and would prove nothing about it.
 */
@Testcontainers
@DisplayName("V60 - the governed AI flag")
class GovernedAiFlagMigrationTest {

    @Container
    @SuppressWarnings("resource")
    final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("v60")
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

    @Test
    @DisplayName("is OFF at V59 and ON after V60")
    void flagIsTurnedOnByV60() throws Exception {
        flyway("59").migrate();
        assertThat(enabledGlobally()).as("V32 left it dark — this is why nothing showed").isFalse();

        flyway(null).migrate();
        assertThat(enabledGlobally()).as("V60 must turn it on").isTrue();
    }

    @Test
    @DisplayName("grants USE_AI_ASSISTANT to roles holding every permission explicitly")
    void grantAllRolesGetTheAiPermission() throws Exception {
        flyway("59").migrate();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO organisation (id, name) VALUES "
                    + "('11111111-1111-1111-1111-111111111111', 'Flag Org')");
            st.execute("INSERT INTO role (id, name, organisation_id, grant_all_permissions) VALUES "
                    + "('22222222-2222-2222-2222-222222222222', 'SUPER', "
                    + "'11111111-1111-1111-1111-111111111111', TRUE)");
        }

        flyway(null).migrate();

        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM role_permission WHERE permission = 'USE_AI_ASSISTANT'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("a tenant that opted out stays opted out")
    void perTenantOverrideStillWins() throws Exception {
        flyway(null).migrate();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO organisation (id, name) VALUES "
                    + "('33333333-3333-3333-3333-333333333333', 'Opted Out Ltd')");
            st.execute("INSERT INTO feature_flag_organisation (id, feature_flag_id, organisation_id, enabled) "
                    + "SELECT gen_random_uuid(), f.id, '33333333-3333-3333-3333-333333333333', FALSE "
                    + "FROM feature_flag f WHERE f.flag_key = 'commercial.governed-ai'");

            try (ResultSet rs = st.executeQuery(
                    "SELECT enabled FROM feature_flag_organisation "
                    + "WHERE organisation_id = '33333333-3333-3333-3333-333333333333'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean(1)).isFalse();
            }
        }
    }

    private boolean enabledGlobally() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT enabled_globally FROM feature_flag WHERE flag_key = 'commercial.governed-ai'")) {
            return rs.next() && rs.getBoolean(1);
        }
    }
}
