package com.assetiq.controllers.v1;

import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each database integrity SQLState gets its own honest status, errorCode and,
 * where known, a field error, instead of the old blanket 409 "already exists".
 */
class GlobalExceptionHandlerDataIntegrityTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static DataIntegrityViolationException wrap(SQLException sql, String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
                new org.hibernate.exception.ConstraintViolationException("could not execute statement", sql, constraint));
    }

    private static DataIntegrityViolationException wrap(SQLException sql) {
        return new DataIntegrityViolationException("could not execute statement", sql);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(ResponseEntity<Object> response) {
        return (Map<String, Object>) response.getBody();
    }

    @Test
    void uniqueViolation_knownConstraint_is409DuplicateNamingTheField() {
        SQLException sql = new SQLException(
                "ERROR: duplicate key value violates unique constraint \"uk_supplier_email_per_org\"\n"
                        + "  Detail: Key (email, organisation_id)=(a@b.com, 1) already exists.", "23505");

        ResponseEntity<Object> response = handler.handleDataIntegrity(wrap(sql, "uk_supplier_email_per_org"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        Map<String, Object> body = body(response);
        assertThat(body.get("errorCode")).isEqualTo("DUPLICATE");
        assertThat(body.get("errors")).isEqualTo(Map.of("email", "already in use"));
        // The conflicting value must never be echoed back.
        assertThat(body.toString()).doesNotContain("a@b.com");
    }

    @Test
    void uniqueViolation_partialIndexName_mapsToField() {
        SQLException sql = new SQLException(
                "ERROR: duplicate key value violates unique constraint \"uq_asset_org_tag_live\"", "23505");

        Map<String, Object> body = body(handler.handleDataIntegrity(wrap(sql)));

        assertThat(body.get("errors")).isEqualTo(Map.of("assetTag", "already in use"));
        assertThat(body.get("message")).isEqualTo("A record with this asset tag already exists");
    }

    @Test
    void uniqueViolation_unknownConstraint_fallsBackToKeyColumns() {
        PSQLException sql = new PSQLException(
                "ERROR: duplicate key value violates unique constraint \"some_new_index\"\n"
                        + "  Detail: Key (organisation_id, invoice_number)=(x, y) already exists.",
                PSQLState.UNIQUE_VIOLATION);

        Map<String, Object> body = body(handler.handleDataIntegrity(wrap(sql)));

        assertThat(body.get("errorCode")).isEqualTo("DUPLICATE");
        assertThat(body.get("errors")).isEqualTo(Map.of("invoiceNumber", "already in use"));
    }

    @Test
    void uniqueViolation_nothingKnown_isGenericDuplicateWithoutErrors() {
        SQLException sql = new SQLException("duplicate", "23505");

        ResponseEntity<Object> response = handler.handleDataIntegrity(wrap(sql));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(body(response).get("errorCode")).isEqualTo("DUPLICATE");
        assertThat(body(response)).doesNotContainKey("errors");
        assertThat(body(response).get("message")).isEqualTo("A record with this value already exists");
    }

    @Test
    void notNullViolation_is400ValidationFailedWithColumnField() {
        SQLException sql = new SQLException(
                "ERROR: null value in column \"category_id\" of relation \"asset\" violates not-null constraint", "23502");

        ResponseEntity<Object> response = handler.handleDataIntegrity(wrap(sql));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(body(response).get("errorCode")).isEqualTo("VALIDATION_FAILED");
        assertThat(body(response).get("errors")).isEqualTo(Map.of("categoryId", "is required"));
    }

    @Test
    void stringTooLong_is400ValueTooLong() {
        SQLException sql = new SQLException("ERROR: value too long for type character varying(32)", "22001");

        ResponseEntity<Object> response = handler.handleDataIntegrity(wrap(sql));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(body(response).get("errorCode")).isEqualTo("VALUE_TOO_LONG");
        assertThat(body(response).get("message")).isEqualTo("A value is too long for its field");
    }

    @Test
    void numericOverflow_is400NumberOutOfRange() {
        SQLException sql = new SQLException("ERROR: numeric field overflow", "22003");

        ResponseEntity<Object> response = handler.handleDataIntegrity(wrap(sql));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(body(response).get("errorCode")).isEqualTo("NUMBER_OUT_OF_RANGE");
    }

    @Test
    void foreignKeyOnDelete_is409InUse() {
        SQLException sql = new SQLException(
                "ERROR: update or delete on table \"category\" violates foreign key constraint \"fk_x\" on table \"asset\"",
                "23503");

        ResponseEntity<Object> response = handler.handleDataIntegrity(wrap(sql));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(body(response).get("errorCode")).isEqualTo("IN_USE");
        assertThat(body(response).get("message")).isEqualTo("This record is still in use by other records");
    }

    @Test
    void foreignKeyOnInsert_is409InUseWithMissingLinkMessage() {
        SQLException sql = new SQLException(
                "ERROR: insert or update on table \"asset\" violates foreign key constraint \"fk_x\"", "23503");

        ResponseEntity<Object> response = handler.handleDataIntegrity(wrap(sql));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(body(response).get("errorCode")).isEqualTo("IN_USE");
        assertThat(body(response).get("message"))
                .isEqualTo("A linked record does not exist or is no longer available");
    }

    @Test
    void checkViolation_is400() {
        SQLException sql = new SQLException(
                "ERROR: new row for relation \"budget_ledger_entry\" violates check constraint \"ck_amount\"", "23514");

        ResponseEntity<Object> response = handler.handleDataIntegrity(wrap(sql));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(body(response).get("errorCode")).isEqualTo("CHECK_VIOLATION");
    }

    @Test
    void noSqlState_isGenericConflict() {
        ResponseEntity<Object> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("something"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(body(response).get("errorCode")).isEqualTo("CONFLICT");
        assertThat(body(response)).containsKeys("status", "message", "timestamp");
    }
}
