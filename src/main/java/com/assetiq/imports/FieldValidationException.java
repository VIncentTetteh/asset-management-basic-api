package com.assetiq.imports;

/**
 * A single cell was wrong.
 *
 * <p>Carries the field name so the engine can name the column <em>as the user wrote
 * it</em> in the error, not as AssetIQ names it internally. Someone migrating 3000
 * assets has to be able to find the cell.</p>
 */
public class FieldValidationException extends RuntimeException {

    private final String fieldName;

    public FieldValidationException(String fieldName, String message) {
        super(message);
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}
