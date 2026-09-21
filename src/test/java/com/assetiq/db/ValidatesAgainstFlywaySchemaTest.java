package com.assetiq.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the whole application against a Flyway-built PostgreSQL schema with
 * {@code ddl-auto=validate}, exactly as production does.
 *
 * <h3>The gap this closes</h3>
 * Nothing else in this project ever put the entities and the migrations in the same
 * room:
 *
 * <ul>
 *   <li>The test profile runs H2 with {@code ddl-auto=create-drop} and Flyway
 *       <em>disabled</em>, so the schema is generated <em>from</em> the entities.
 *       They cannot disagree there, by construction.</li>
 *   <li>{@code FlywayMigrationTest} applies the full migration chain to real
 *       Postgres, but over plain JDBC — no Spring context, so no entity mapping is
 *       ever compared against the result.</li>
 *   <li>The CI migration-integrity job does the same: migrations run, application
 *       never starts.</li>
 * </ul>
 *
 * So every layer was individually green while the two definitions drifted apart.
 * It surfaced the only place it could — the first real deployment — where the
 * container crash-looped on:
 *
 * <pre>
 *   Schema-validation: wrong column type encountered in column [currency] in
 *   table [asset]; found [bpchar (Types#CHAR)], but expecting [varchar(255)]
 * </pre>
 *
 * Fourteen columns were affected, and {@code validate} reports only the first, so
 * this is also the only practical way to find them all: fix one, run again, repeat
 * until the context starts.
 *
 * <p>Building the EntityManagerFactory <em>is</em> the assertion. Hibernate runs
 * schema validation during that build, so a mismatch fails this test at context
 * load with the offending table and column named. The body then just confirms the
 * factory really is open, so an accidental change to lazy initialisation cannot
 * quietly turn this into a test that asserts nothing.
 */
@Testcontainers
@SpringBootTest
@DisplayName("Entities validate against the Flyway-built schema")
class ValidatesAgainstFlywaySchemaTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("assetiq_validate")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");

        // The two settings that make this test mean something. Flyway owns the
        // schema and Hibernate only checks it, which is precisely the production
        // arrangement and the opposite of the H2 profile's create-drop.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("the persistence unit builds, so every mapping matches a real column")
    void entitiesMatchTheMigratedSchema() {
        assertThat(entityManagerFactory)
                .describedAs("context started, so Hibernate validated every mapping "
                        + "against the Flyway schema without complaint")
                .isNotNull();
        assertThat(entityManagerFactory.isOpen()).isTrue();
    }

    /**
     * {@code validate} only checks that mapped columns exist. It is blind to the
     * opposite drift: a legacy V7 column that no entity maps any more but that is
     * still NOT NULL without a default. Every insert into that table then fails at
     * runtime (V24 fixed checkout_records.user_id this way; asset_audit.asset_id
     * broke scheduling audits the same way). This lists every such column in the
     * tables the entities write to.
     */
    @Test
    @DisplayName("no NOT NULL column without a default is invisible to the entity that inserts into its table")
    void noOrphanedNotNullColumns() {
        Map<String, Set<String>> mapped = new HashMap<>();
        var metamodel = entityManagerFactory.unwrap(SessionFactoryImplementor.class).getMappingMetamodel();
        metamodel.forEachEntityDescriptor(persister -> persister.forEachSelectable((i, selectable) ->
                mapped.computeIfAbsent(selectable.getContainingTableExpression().toLowerCase(Locale.ROOT),
                        t -> new HashSet<>()).add(selectable.getSelectionExpression().toLowerCase(Locale.ROOT))));
        metamodel.forEachEntityDescriptor(persister ->
                mapped.computeIfAbsent(persister.getIdentifierTableName().toLowerCase(Locale.ROOT), t -> new HashSet<>())
                        .add(persister.getIdentifierColumnNames()[0].toLowerCase(Locale.ROOT)));

        List<String> orphans = new ArrayList<>();
        jdbcTemplate.query("""
                SELECT table_name, column_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND is_nullable = 'NO' AND column_default IS NULL
                """, rs -> {
            String table = rs.getString(1).toLowerCase(Locale.ROOT);
            String column = rs.getString(2).toLowerCase(Locale.ROOT);
            Set<String> columns = mapped.get(table);
            if (columns != null && !columns.contains(column)) orphans.add(table + "." + column);
        });
        assertThat(orphans).describedAs("NOT NULL columns no entity writes").isEmpty();
    }
}
