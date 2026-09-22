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
 * V46 makes natural keys unique among live rows: soft-deleted rows no longer
 * block reuse, live duplicates are refused, and existing live duplicates make
 * the migration fail loudly instead of being cleaned up silently.
 */
@Testcontainers
class PartialUniqueIndexMigrationTest {

    /** One fresh database per test (instance field): each test needs its own migration history. */
    @Container
    @SuppressWarnings("resource")
    final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("v46")
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

    private static void po(Statement st, UUID org, String number, boolean deleted) throws SQLException {
        st.execute("INSERT INTO purchase_order (id, organisation_id, po_number, deleted_at) VALUES ('"
                + UUID.randomUUID() + "', '" + org + "', '" + number + "', " + (deleted ? "now()" : "NULL") + ")");
    }

    private static void asset(Statement st, UUID org, String tag, boolean deleted) throws SQLException {
        st.execute("INSERT INTO asset (id, organisation_id, name, asset_tag, deleted_at) VALUES ('"
                + UUID.randomUUID() + "', '" + org + "', 'Laptop', '" + tag + "', "
                + (deleted ? "now()" : "NULL") + ")");
    }

    @Test
    void liveKeysAreUnique_softDeletedRowsDoNotBlockReuse() throws Exception {
        flyway(null).migrate();

        try (Connection c = connect(); Statement st = c.createStatement()) {
            UUID org = organisation(st);

            // A soft-deleted asset's tag, and a soft-deleted PO's number, can be reused.
            asset(st, org, "TAG-1", true);
            asset(st, org, "TAG-1", false);
            po(st, org, "PO-1", true);
            po(st, org, "PO-1", false);

            assertThatThrownBy(() -> asset(st, org, "TAG-1", false))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uq_asset_org_tag_live")
                    .extracting(e -> ((SQLException) e).getSQLState()).isEqualTo("23505");
            assertThatThrownBy(() -> po(st, org, "PO-1", false))
                    .hasMessageContaining("uq_purchase_order_org_number_live");

            // Another tenant may use the same key.
            UUID other = organisation(st);
            po(st, other, "PO-1", false);

            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM pg_indexes WHERE schemaname = 'public'"
                    + " AND indexname IN ('uq_purchase_order_org_number_live', 'uq_cloud_asset_org_resource_live',"
                    + " 'uq_cloud_cost_record_asset_month_service_live', 'uq_discovered_device_org_ip_live',"
                    + " 'uq_bog_control_org_ref_live', 'uq_pci_saq_org_requirement_live',"
                    + " 'uq_sla_metric_org_period_live', 'uq_asset_org_tag_live', 'uq_asset_org_serial_live',"
                    + " 'uq_supplier_org_email_live', 'uq_supplier_org_tax_id_live', 'uq_supplier_org_regnum_live')"
                    + " AND indexdef LIKE '%WHERE%deleted_at IS NULL%'")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(12);
            }
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM pg_constraint c"
                    + " JOIN pg_namespace n ON n.oid = c.connamespace WHERE n.nspname = 'public' AND c.conname IN"
                    + " ('uk_asset_tag_per_organisation', 'uk_serial_number_per_organisation',"
                    + " 'uk_supplier_email_per_org', 'uk_supplier_taxid_per_org', 'uk_supplier_regnum_per_org')")) {
                rs.next();
                assertThat(rs.getInt(1)).as("full constraints replaced by the partial indexes").isZero();
            }
        }
    }

    @Test
    void existingLiveDuplicatesFailTheMigrationWithoutTouchingData() throws Exception {
        flyway("45").migrate();
        UUID org;
        try (Connection c = connect(); Statement st = c.createStatement()) {
            org = organisation(st);
            // Only JPA declared this key before V46, so the database accepted duplicates.
            po(st, org, "PO-7", false);
            po(st, org, "PO-7", false);
        }

        assertThatThrownBy(() -> flyway(null).migrate())
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("live duplicate group(s) in purchase_order");

        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM purchase_order WHERE po_number = 'PO-7'")) {
            rs.next();
            assertThat(rs.getInt(1)).as("no data deleted").isEqualTo(2);
        }
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM pg_constraint c JOIN pg_namespace n"
                     + " ON n.oid = c.connamespace WHERE n.nspname = 'public'"
                     + " AND c.conname = 'uk_asset_tag_per_organisation'")) {
            rs.next();
            assertThat(rs.getInt(1)).as("rolled back: the old asset constraint is still there").isEqualTo(1);
        }
    }
}
