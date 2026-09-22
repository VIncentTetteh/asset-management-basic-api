package com.assetiq.exceptions;

/**
 * A 409 about one request field whose value clashes with an existing record,
 * found by the service before the database's unique index would. Answered like
 * a unique-index violation: {@code DUPLICATE} with {@code errors: {field: "already in use"}},
 * so the web app marks the field.
 */
public class DuplicateFieldException extends RuntimeException {

    private final String field;

    public DuplicateFieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
