package com.assetiq.exceptions;

/**
 * A 400 about one request field, found by the service rather than Bean
 * Validation (e.g. a wrong current password). Answered like a Bean Validation
 * failure: {@code VALIDATION_FAILED} with {@code errors: {field: message}}, so
 * the web app marks the field.
 */
public class FieldValidationException extends RuntimeException {

    private final String field;

    public FieldValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
