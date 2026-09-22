package com.assetiq.validation;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps request validation at least as strict as the database.
 *
 * <p>A request DTO that accepts a value its column cannot store used to fail as a
 * database error (and, before the SQLState split, as a misleading 409
 * "already exists"). This test puts three definitions in one room: every request
 * body type used by a controller, the entity it maps to, and the <em>real</em>
 * column as the Flyway migrations build it (the database is the source of truth:
 * production runs {@code ddl-auto=validate}, which checks neither lengths nor
 * nullability).
 *
 * <p>For every writable DTO field with a same-named entity column:
 * <ul>
 *   <li>String in a length-limited column: {@code @Size(max <= length)};</li>
 *   <li>BigDecimal in a NUMERIC(p, s) column: {@code @Digits(integer <= p - s, fraction <= s)};</li>
 *   <li>NOT NULL column (or entity {@code nullable = false}): {@code @NotNull}/{@code @NotBlank}
 *       in some group, unless the entity supplies a default or the field is listed in
 *       {@link #NOT_NULL_EXEMPTIONS} with the reason.</li>
 * </ul>
 */
@Testcontainers
class DtoColumnConstraintConsistencyTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("dto_consistency")
            .withUsername("postgres")
            .withPassword("postgres");

    private static final List<String> ENTITY_PACKAGES = List.of(
            "com.assetiq.models.", "com.assetiq.models.compliance.", "com.assetiq.dpa.model.");

    /** Request types whose entity is not named after them. */
    private static final Map<String, String> EXPLICIT_ENTITY = Map.of(
            "CreateDsarRequest", "com.assetiq.dpa.model.DsarRequest",
            "CreateConsentRequest", "com.assetiq.dpa.model.ConsentRecord",
            "RegisterRequest", "com.assetiq.models.User");

    /** Never client-controlled, whatever the DTO says: tenant scoping and audit columns. */
    private static final Set<String> SERVER_SET_FIELDS = Set.of(
            "id", "organisationId", "createdAt", "updatedAt", "deletedAt", "createdBy", "modifiedBy");

    /**
     * NOT NULL columns a DTO may leave out, and why. Keep this short and specific:
     * the fix for a new failure is almost always a constraint on the DTO.
     */
    private static final Map<String, String> NOT_NULL_EXEMPTIONS = Map.ofEntries(
            Map.entry("CheckoutRecordDto.checkedOutAt", "stamped with now() by CheckoutServiceImpl"),
            Map.entry("ExchangeRateDto.effectiveDate", "defaults to today in ExchangeRateServiceImpl"),
            Map.entry("LeaseRecordDto.status", "create sets ACTIVE; update keeps the stored status when null"),
            Map.entry("OrganisationDto.billingCurrency",
                    "derived from the country on create; update keeps the stored value when blank"),
            Map.entry("RiskRegisterDto.riskScore", "computed from likelihood x impact in @PrePersist/@PreUpdate"));

    private static Map<String, Map<String, DbColumn>> schema;

    private static final Set<String> usedExemptions = new TreeSet<>();

    record DbColumn(String type, Integer maxLength, Integer precision, Integer scale, boolean notNull) {
    }

    @BeforeAll
    static void readFlywaySchema() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        schema = new HashMap<>();
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT table_name, column_name, data_type, character_maximum_length,"
                     + " numeric_precision, numeric_scale, is_nullable FROM information_schema.columns"
                     + " WHERE table_schema = 'public'")) {
            while (rs.next()) {
                schema.computeIfAbsent(rs.getString(1), t -> new HashMap<>()).put(rs.getString(2),
                        new DbColumn(rs.getString(3), (Integer) rs.getObject(4), (Integer) rs.getObject(5),
                                (Integer) rs.getObject(6), "NO".equals(rs.getString(7))));
            }
        }
    }

    @Test
    void requestDtosAreAtLeastAsStrictAsTheirColumns() {
        List<String> violations = new ArrayList<>();
        Set<String> checkedPairs = new TreeSet<>();
        usedExemptions.clear();

        for (Class<?> dto : requestBodyTypes()) {
            Optional<Class<?>> entity = entityFor(dto);
            if (entity.isEmpty()) continue;
            String table = tableName(entity.get());
            Map<String, DbColumn> columns = schema.get(table);
            assertThat(columns).as("table %s for %s", table, entity.get().getSimpleName()).isNotNull();
            checkedPairs.add(dto.getSimpleName() + " -> " + table);

            for (Field field : allFields(dto)) {
                if (!isWritable(field) || SERVER_SET_FIELDS.contains(field.getName())) continue;
                Optional<Field> entityField = entityField(entity.get(), field.getName());
                if (entityField.isEmpty() || !isColumn(entityField.get())) continue;
                DbColumn column = columns.get(columnName(entityField.get()));
                if (column == null) continue;
                String where = dto.getSimpleName() + "." + field.getName();

                if (field.getType() == String.class) {
                    checkLength(where, field, entityField.get(), column, violations);
                }
                if (field.getType() == BigDecimal.class) {
                    checkDigits(where, field, column, violations);
                }
                checkNotNull(where, field, entity.get(), entityField.get(), column, violations);
            }
        }

        assertThat(checkedPairs).as("DTO/entity pairs discovered").hasSizeGreaterThan(30);
        assertThat(usedExemptions)
                .as("stale NOT NULL exemptions: remove the ones no longer needed")
                .containsExactlyInAnyOrderElementsOf(NOT_NULL_EXEMPTIONS.keySet());
        assertThat(violations)
                .as("DTO fields looser than their database columns (checked %s)", checkedPairs)
                .isEmpty();
    }

    // ── checks ────────────────────────────────────────────────────────────────

    private static void checkLength(String where, Field field, Field entityField, DbColumn column,
                                    List<String> violations) {
        Integer limit = column.maxLength();
        Column jpa = entityField.getAnnotation(Column.class);
        boolean text = column.type().equals("text");
        if (limit == null && !text && jpa != null && jpa.columnDefinition().isEmpty()) {
            limit = jpa.length();
        }
        if (limit == null) return;
        Size size = field.getAnnotation(Size.class);
        if (size == null) {
            violations.add(where + ": column is varchar(" + limit + ") but the DTO has no @Size(max=" + limit + ")");
        } else if (size.max() > limit) {
            violations.add(where + ": @Size(max=" + size.max() + ") exceeds column length " + limit);
        }
    }

    private static void checkDigits(String where, Field field, DbColumn column, List<String> violations) {
        if (!"numeric".equals(column.type()) || column.precision() == null || column.scale() == null) return;
        int integer = column.precision() - column.scale();
        int fraction = column.scale();
        Digits digits = field.getAnnotation(Digits.class);
        if (digits == null) {
            violations.add(where + ": column is NUMERIC(" + column.precision() + "," + fraction
                    + ") but the DTO has no @Digits(integer=" + integer + ", fraction=" + fraction + ")");
        } else if (digits.integer() > integer || digits.fraction() > fraction) {
            violations.add(where + ": @Digits(integer=" + digits.integer() + ", fraction=" + digits.fraction()
                    + ") is looser than NUMERIC(" + column.precision() + "," + fraction + ")");
        }
    }

    private static void checkNotNull(String where, Field field, Class<?> entity, Field entityField,
                                     DbColumn column, List<String> violations) {
        Column jpa = entityField.getAnnotation(Column.class);
        boolean required = column.notNull() || (jpa != null && !jpa.nullable());
        if (!required || field.getType().isPrimitive()) return;
        if (field.isAnnotationPresent(NotNull.class) || field.isAnnotationPresent(NotBlank.class)
                || field.isAnnotationPresent(NotEmpty.class)) {
            return;
        }
        if (NOT_NULL_EXEMPTIONS.containsKey(where)) {
            usedExemptions.add(where);
            return;
        }
        if (hasEntityDefault(entity, entityField)) return;
        violations.add(where + ": column is NOT NULL but the DTO has no @NotNull/@NotBlank"
                + " (add one, or an exemption with the reason)");
    }

    // ── discovery ─────────────────────────────────────────────────────────────

    private static Set<Class<?>> requestBodyTypes() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<Class<?>> types = new java.util.LinkedHashSet<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.assetiq")) {
            Class<?> controller = load(bd.getBeanClassName());
            for (Method m : controller.getDeclaredMethods()) {
                for (Parameter p : m.getParameters()) {
                    if (p.isAnnotationPresent(RequestBody.class) && p.getType().getName().startsWith("com.assetiq.")) {
                        types.add(p.getType());
                    }
                }
            }
        }
        return types;
    }

    private static Optional<Class<?>> entityFor(Class<?> dto) {
        String explicit = EXPLICIT_ENTITY.get(dto.getSimpleName());
        if (explicit != null) return Optional.of(load(explicit));
        String base = dto.getSimpleName().replaceAll("Dto$", "");
        for (String pkg : ENTITY_PACKAGES) {
            try {
                Class<?> c = Class.forName(pkg + base);
                if (c.isAnnotationPresent(jakarta.persistence.Entity.class)) return Optional.of(c);
            } catch (ClassNotFoundException ignored) {
                // try the next package
            }
        }
        return Optional.empty();
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<Field> allFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) fields.add(f);
            }
        }
        return fields;
    }

    private static Optional<Field> entityField(Class<?> entity, String name) {
        return allFields(entity).stream().filter(f -> f.getName().equals(name)).findFirst();
    }

    private static boolean isWritable(Field field) {
        JsonProperty json = field.getAnnotation(JsonProperty.class);
        return json == null || json.access() != JsonProperty.Access.READ_ONLY;
    }

    private static boolean isColumn(Field f) {
        return !f.isAnnotationPresent(Transient.class) && !f.isAnnotationPresent(OneToMany.class)
                && !f.isAnnotationPresent(ManyToMany.class) && !f.isAnnotationPresent(ElementCollection.class)
                && !f.isAnnotationPresent(Embedded.class) && !Modifier.isTransient(f.getModifiers());
    }

    static String tableName(Class<?> entity) {
        Table t = entity.getAnnotation(Table.class);
        return t != null && !t.name().isBlank() ? t.name() : snake(entity.getSimpleName());
    }

    private static String columnName(Field f) {
        Column c = f.getAnnotation(Column.class);
        if (c != null && !c.name().isBlank()) return c.name();
        JoinColumn j = f.getAnnotation(JoinColumn.class);
        if (j != null && !j.name().isBlank()) return j.name();
        if (f.isAnnotationPresent(ManyToOne.class) || f.isAnnotationPresent(OneToOne.class)) {
            return snake(f.getName()) + "_id";
        }
        return snake(f.getName());
    }

    /** Spring's CamelCaseToUnderscoresNamingStrategy. */
    static String snake(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    /** True when a freshly constructed entity already holds a value for the field. */
    private static boolean hasEntityDefault(Class<?> entity, Field entityField) {
        try {
            var ctor = entity.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object instance = ctor.newInstance();
            entityField.setAccessible(true);
            return entityField.get(instance) != null;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
