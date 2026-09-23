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
 * The import could accept a column AssetIQ has no field for and then drop it, because
 * {@code commercial.governed-custom-fields} was seeded OFF in V38 and the custom-field
 * path is gated on it. V66 turns it on.
 *
 * <p>This runs the real migration chain against real Postgres: the main test profile is
 * H2 with Flyway disabled, where an unregistered flag simply reads as false, so it would
 * prove nothing about either migration.</p>
 *
 * <p>The last test here is the one that matters most six months from now. V38 is applied
 * on staging, so its checksum must not change; the only correct way to revisit its
 * decision is a new migration. That is easy to forget and cheap to assert.</p>
 */
@Testcontainers
@DisplayName("V66 - the custom fields flag")
class CustomFieldsFlagMigrationTest {

    private static final String FLAG = "commercial.governed-custom-fields";

    @Container
    @SuppressWarnings("resource")
    final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("v66")
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
    @DisplayName("is OFF at V65 and ON after V66")
    void flagIsTurnedOnByV66() throws Exception {
        flyway("65").migrate();
        assertThat(enabledGlobally())
                .as("V38 contained it — this is why an imported column was silently dropped")
                .isFalse();

        flyway(null).migrate();
        assertThat(enabledGlobally()).as("V66 must turn it on").isTrue();
    }

    @Test
    @DisplayName("the description says what is not governed, rather than claiming it is")
    void theDescriptionIsHonestAboutWhatIsMissing() throws Exception {
        flyway(null).migrate();

        // V38 promised six things and delivered storage. Whatever the row says next, it
        // must not go back to promising them: an operator reading the flag table is
        // entitled to know that switching this on does not bring validation or
        // sensitivity classification with it.
        assertThat(description().toLowerCase(java.util.Locale.ROOT))
                .contains("do not")
                .contains("sensitivity")
                .contains("permissions");
    }

    @Test
    @DisplayName("a tenant that was explicitly switched off stays off")
    void perTenantOverrideStillWins() throws Exception {
        flyway("65").migrate();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO organisation (id, name) VALUES "
                    + "('44444444-4444-4444-4444-444444444444', 'Opted Out Ltd')");
            st.execute("INSERT INTO feature_flag_organisation (id, feature_flag_id, organisation_id, enabled) "
                    + "SELECT gen_random_uuid(), f.id, '44444444-4444-4444-4444-444444444444', FALSE "
                    + "FROM feature_flag f WHERE f.flag_key = '" + FLAG + "'");
        }

        flyway(null).migrate();

        // Granting a capability by default must not overrule a decision somebody made
        // about one customer.
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT enabled FROM feature_flag_organisation "
                             + "WHERE organisation_id = '44444444-4444-4444-4444-444444444444'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean(1)).isFalse();
        }
        assertThat(enabledGlobally()).isTrue();
    }

    @Test
    @DisplayName("the whole chain still applies to an empty database exactly once")
    void theChainIsIdempotentFromScratch() throws Exception {
        flyway(null).migrate();
        // Re-running finds nothing to do rather than doubling the flag row, which the
        // INSERT ... WHERE NOT EXISTS guard in V66 is there to ensure.
        flyway(null).migrate();

        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT count(*) FROM feature_flag WHERE flag_key = '" + FLAG + "'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    /**
     * V38 is applied on staging. Editing it would change its checksum and break the
     * migration chain for every environment that already ran it, so the decision it made
     * is revisited by a later migration and never by an edit.
     */
    @Test
    @DisplayName("V38 still contains the flag; the decision was revisited, not rewritten")
    void v38IsLeftAlone() throws Exception {
        String v38 = new String(getClass().getResourceAsStream(
                "/db/migration/V38__contain_ungoverned_custom_fields.sql").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);

        assertThat(v38).contains("enabled_globally = FALSE");
        assertThat(v38)
                .as("if this fails, someone edited an applied migration instead of adding a new one")
                .doesNotContain("enabled_globally = TRUE");
    }

    private boolean enabledGlobally() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT enabled_globally FROM feature_flag WHERE flag_key = '" + FLAG + "'")) {
            return rs.next() && rs.getBoolean(1);
        }
    }

    private String description() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT description FROM feature_flag WHERE flag_key = '" + FLAG + "'")) {
            return rs.next() ? rs.getString(1) : "";
        }
    }
}
