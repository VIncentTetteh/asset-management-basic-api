package com.assetiq.controllers.v1;

import org.springframework.http.HttpStatus;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a database integrity failure into an honest API error.
 *
 * <p>Before this existed every {@code DataIntegrityViolationException} became
 * {@code 409 "A record with this value already exists"}, so a value that was
 * simply too long, a missing required value, or a numeric overflow all read to
 * the user as a duplicate. The SQLState of the root {@link SQLException} tells
 * these apart, and the constraint (or column) name tells the web app which form
 * field to mark.
 *
 * <p>Only field names ever reach the client. The driver message (which for a
 * unique violation contains the conflicting value) is never echoed back.
 */
final class DataIntegrityViolationClassifier {

    static final String UNIQUE_VIOLATION = "23505";
    static final String NOT_NULL_VIOLATION = "23502";
    static final String FOREIGN_KEY_VIOLATION = "23503";
    static final String CHECK_VIOLATION = "23514";
    static final String STRING_TOO_LONG = "22001";
    static final String NUMERIC_OUT_OF_RANGE = "22003";

    static final String ALREADY_IN_USE = "already in use";
    static final String REQUIRED = "is required";

    /**
     * Unique constraint / index name to the API field it protects. Built from the
     * unique constraints in the Flyway migrations (V7 baseline, V17, V22, V46, V51) and
     * the entity {@code @UniqueConstraint} declarations. Postgres' default names for
     * the V7 inline {@code UNIQUE} columns ({@code <table>_<column>_key}) are listed
     * too, since databases built from V7 carry them.
     */
    static final Map<String, String> UNIQUE_CONSTRAINT_FIELDS = Map.ofEntries(
            // asset (V7 full constraints; V46 live-row partial indexes)
            Map.entry("uk_asset_tag_per_organisation", "assetTag"),
            Map.entry("uk_serial_number_per_organisation", "serialNumber"),
            Map.entry("uq_asset_org_tag_live", "assetTag"),
            Map.entry("uq_asset_org_serial_live", "serialNumber"),
            // supplier
            Map.entry("uk_supplier_email_per_org", "email"),
            Map.entry("uk_supplier_taxid_per_org", "taxId"),
            Map.entry("uk_supplier_regnum_per_org", "registrationNumber"),
            Map.entry("uq_supplier_org_email_live", "email"),
            Map.entry("uq_supplier_org_tax_id_live", "taxId"),
            Map.entry("uq_supplier_org_regnum_live", "registrationNumber"),
            // users and employees
            Map.entry("uk_user_email_per_org", "email"),
            Map.entry("uk_user_employeeid_per_org", "employeeId"),
            Map.entry("uq_employee_org_number", "employeeNumber"),
            Map.entry("uq_employee_org_user", "userId"),
            // organisation
            Map.entry("organisation_name_key", "name"),
            Map.entry("organisation_registration_number_key", "registrationNumber"),
            Map.entry("organisation_tax_id_key", "taxId"),
            Map.entry("organisation_contact_email_key", "contactEmail"),
            Map.entry("idx_organisation_email_domain", "emailDomain"),
            // finance and procurement
            Map.entry("uk_po_number_per_org", "poNumber"),
            Map.entry("uq_purchase_order_org_number_live", "poNumber"),
            Map.entry("uk_license_key_per_org", "licenseKey"),
            Map.entry("uq_exchange_rates_org_pair_date_live", "effectiveDate"),
            // operations
            Map.entry("uq_cloud_asset_org_resource_live", "resourceId"),
            Map.entry("uq_cloud_cost_record_asset_month_service_live", "serviceName"),
            Map.entry("uq_discovered_device_org_ip_live", "ipAddress"),
            Map.entry("uk_asset_custom_field_name", "fieldName"),
            // compliance
            Map.entry("uq_bog_ctrl_org_ref", "directiveRef"),
            Map.entry("uq_bog_control_org_ref_live", "directiveRef"),
            Map.entry("uq_pci_saq_org_req", "requirementNumber"),
            Map.entry("uq_pci_saq_org_requirement_live", "requirementNumber"),
            Map.entry("uq_sla_org_month_year", "month"),
            Map.entry("uq_sla_metric_org_period_live", "month"),
            // roles
            Map.entry("uq_rp_role_permission", "permissions"));

    /** Columns that scope a key rather than identify the conflicting field. */
    private static final Set<String> SCOPE_COLUMNS = Set.of("organisation_id", "deleted_at");

    private static final Pattern QUOTED_CONSTRAINT = Pattern.compile("constraint \"([^\"]+)\"");
    private static final Pattern KEY_COLUMNS = Pattern.compile("Key \\(([^)]+)\\)=");
    private static final Pattern NULL_COLUMN = Pattern.compile("null value in column \"([^\"]+)\"");

    private DataIntegrityViolationClassifier() {
    }

    /** What the handler should answer. {@code fieldErrors} is empty when no field is known. */
    record Classification(HttpStatus status, String errorCode, String message,
                          Map<String, String> fieldErrors, String sqlState, String constraint) {
    }

    static Classification classify(Throwable ex) {
        SQLException sql = rootSqlException(ex);
        String state = sql != null ? sql.getSQLState() : null;
        String text = chainText(ex);
        String constraint = constraintName(ex, text);

        if (state == null) {
            return generic(null, constraint);
        }
        switch (state) {
            case UNIQUE_VIOLATION: {
                Optional<String> field = uniqueField(constraint, text);
                return new Classification(HttpStatus.CONFLICT, "DUPLICATE",
                        field.map(f -> "A record with this " + humanise(f) + " already exists")
                                .orElse("A record with this value already exists"),
                        field.map(f -> Map.of(f, ALREADY_IN_USE)).orElse(Map.of()), state, constraint);
            }
            case NOT_NULL_VIOLATION: {
                Optional<String> field = match(NULL_COLUMN, text).map(DataIntegrityViolationClassifier::camel);
                return new Classification(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                        field.map(f -> humaniseCapitalised(f) + " is required")
                                .orElse("A required value is missing"),
                        field.map(f -> Map.of(f, REQUIRED)).orElse(Map.of()), state, constraint);
            }
            case STRING_TOO_LONG:
                return new Classification(HttpStatus.BAD_REQUEST, "VALUE_TOO_LONG",
                        "A value is too long for its field", Map.of(), state, constraint);
            case NUMERIC_OUT_OF_RANGE:
                return new Classification(HttpStatus.BAD_REQUEST, "NUMBER_OUT_OF_RANGE",
                        "A number is out of range for its field", Map.of(), state, constraint);
            case FOREIGN_KEY_VIOLATION:
                return new Classification(HttpStatus.CONFLICT, "IN_USE",
                        isReferencedFromElsewhere(text)
                                ? "This record is still in use by other records"
                                : "A linked record does not exist or is no longer available",
                        Map.of(), state, constraint);
            case CHECK_VIOLATION:
                return new Classification(HttpStatus.BAD_REQUEST, "CHECK_VIOLATION",
                        "A value is outside the allowed range", Map.of(), state, constraint);
            default:
                return generic(state, constraint);
        }
    }

    private static Classification generic(String state, String constraint) {
        return new Classification(HttpStatus.CONFLICT, "CONFLICT",
                "The request conflicts with existing data", Map.of(), state, constraint);
    }

    /** The deepest SQLException in the cause chain (the driver's own error). */
    static SQLException rootSqlException(Throwable ex) {
        SQLException found = null;
        Set<Throwable> seen = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Throwable t = ex; t != null && seen.add(t); t = t.getCause()) {
            if (t instanceof SQLException s) {
                found = s;
                if (s.getNextException() != null && s.getNextException().getSQLState() != null) {
                    found = s.getNextException();
                }
            }
        }
        return found;
    }

    private static String constraintName(Throwable ex, String text) {
        Set<Throwable> seen = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Throwable t = ex; t != null && seen.add(t); t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve
                    && cve.getConstraintName() != null && !cve.getConstraintName().isBlank()) {
                return normaliseConstraint(cve.getConstraintName());
            }
        }
        return match(QUOTED_CONSTRAINT, text).map(DataIntegrityViolationClassifier::normaliseConstraint)
                .orElse(null);
    }

    /** H2 and some Hibernate versions report {@code "PUBLIC.UK_X_INDEX_4 ON ..."}: keep the bare name. */
    private static String normaliseConstraint(String raw) {
        String name = raw.trim();
        int space = name.indexOf(' ');
        if (space > 0) name = name.substring(0, space);
        int dot = name.lastIndexOf('.');
        if (dot >= 0) name = name.substring(dot + 1);
        return name.replace("\"", "").toLowerCase(java.util.Locale.ROOT);
    }

    private static Optional<String> uniqueField(String constraint, String text) {
        if (constraint != null) {
            String mapped = UNIQUE_CONSTRAINT_FIELDS.get(constraint);
            if (mapped != null) return Optional.of(mapped);
        }
        // Fallback: the key columns Postgres lists in the error detail, e.g.
        // "Key (email, organisation_id)=(...)". Only the column names are used.
        return match(KEY_COLUMNS, text).flatMap(cols -> {
            String[] parts = cols.split(",");
            String field = null;
            for (String part : parts) {
                String col = part.trim().replaceAll("^lower\\((.*)\\)$", "$1");
                if (SCOPE_COLUMNS.contains(col)) continue;
                if (field != null) return Optional.empty();
                field = camel(col);
            }
            return Optional.ofNullable(field);
        });
    }

    /**
     * 23503 fires both when deleting a row others still point at and when saving
     * a row that points at a missing one. Postgres words the second case
     * "insert or update on table"; anything else is treated as "still in use".
     */
    private static boolean isReferencedFromElsewhere(String text) {
        return !text.toLowerCase(java.util.Locale.ROOT).contains("insert or update on table");
    }

    private static Optional<String> match(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private static String chainText(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        Set<Throwable> seen = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Throwable t = ex; t != null && seen.add(t); t = t.getCause()) {
            if (t.getMessage() != null) sb.append(t.getMessage()).append('\n');
            if (t instanceof SQLException s && s.getNextException() != null
                    && s.getNextException().getMessage() != null) {
                sb.append(s.getNextException().getMessage()).append('\n');
            }
        }
        return sb.toString();
    }

    /** {@code category_id} to {@code categoryId}. */
    static String camel(String column) {
        String col = column.toLowerCase(java.util.Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : col.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return sb.toString();
    }

    /** {@code assetTag} to {@code asset tag}. */
    static String humanise(String field) {
        return field.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(java.util.Locale.ROOT);
    }

    private static String humaniseCapitalised(String field) {
        String h = humanise(field);
        return h.isEmpty() ? h : Character.toUpperCase(h.charAt(0)) + h.substring(1);
    }

}
