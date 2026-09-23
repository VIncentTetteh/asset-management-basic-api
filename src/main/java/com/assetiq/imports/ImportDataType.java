package com.assetiq.imports;

/**
 * The shape of a value in an import column.
 *
 * <p>This is deliberately a presentation-and-parsing vocabulary rather than a Java type:
 * the wizard shows it to a human who is deciding which of their columns to point at a
 * field, and the row parser uses the same value to decide how to read the cell. One
 * declaration, both consumers — see {@link ImportFieldDescriptor}.</p>
 */
public enum ImportDataType {
    /** Short free text. */
    STRING,
    /** Long free text (notes, descriptions). */
    TEXT,
    /** Whole number. */
    INTEGER,
    /** Fixed-point number; money and quantities. */
    DECIMAL,
    /** ISO-8601 calendar date, {@code YYYY-MM-DD}, or a real Excel date cell. */
    DATE,
    /** true/false, yes/no, 1/0, y/n. */
    BOOLEAN,
    /** One of {@link ImportFieldDescriptor#enumValues()}. */
    ENUM,
    /** An email address. */
    EMAIL,
    /**
     * A human-readable name that is resolved server-side to another record
     * (category, location, supplier, department, employee). Never a UUID: nobody
     * migrating from another platform has AssetIQ ids.
     */
    REFERENCE
}
