package com.assetiq.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.Locale;

/**
 * A field a tenant has that AssetIQ does not ship: "Warranty Provider", "PO Line",
 * "Room Code".
 *
 * <p>The values live where they always have, in {@link AssetCustomField} against one
 * asset. What was missing was any record that the <em>field</em> exists. Without it, a
 * spreadsheet column turned into three thousand loose key-value rows and the tenant had
 * no way to see, or govern, what their import had added to their schema.</p>
 *
 * <p>Definitions are matched on {@link #fieldKey}, the name with case, whitespace and
 * punctuation collapsed, so a second file headed {@code cost_centre} lands on the field
 * the first file created as {@code Cost Centre} rather than making a rival.</p>
 */
@Entity
@Getter
@Setter
@Table(name = "custom_field_definition")
public class CustomFieldDefinition extends BaseEntity {

    /** Longest name accepted, matching the {@code asset_custom_field.field_name} column. */
    public static final int MAX_NAME_LENGTH = 100;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    /** An {@link com.assetiq.imports.ImportEntityType} name. Only ASSETS stores values today. */
    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType = "ASSETS";

    /** The name as the user wrote it, sanitised and capped. */
    @Column(name = "field_name", nullable = false, length = MAX_NAME_LENGTH)
    private String fieldName;

    /** The comparison form of {@link #fieldName}. Unique per tenant per entity type. */
    @Column(name = "field_key", nullable = false, length = MAX_NAME_LENGTH)
    private String fieldKey;

    /**
     * Advisory shape of the values, inferred from what the import sampled. Values are
     * still stored as text, so a wrong inference costs a rendering hint and never a row.
     */
    @Column(name = "data_type", nullable = false, length = 20)
    private String dataType = "STRING";

    /** {@code IMPORT} or {@code MANUAL} — where this definition came from. */
    @Column(name = "source", nullable = false, length = 20)
    private String source = "IMPORT";

    /**
     * Strips a spreadsheet header down to something usable as a field name: control
     * characters and the leading punctuation Excel uses for formulas removed, whitespace
     * collapsed, length capped.
     *
     * <p>Capping rather than rejecting is deliberate. A header of 140 characters is
     * somebody's sentence-long note, and truncating it keeps their data; refusing it
     * sends them back to Excel, which is the outcome this whole path exists to remove.</p>
     */
    public static String sanitiseName(String header) {
        if (header == null) return "";
        String cleaned = header.replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll("^[=+\\-@\\t\\r]+", "")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.length() <= MAX_NAME_LENGTH ? cleaned : cleaned.substring(0, MAX_NAME_LENGTH).trim();
    }

    /** The comparison form: lower case, letters and digits only. */
    public static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
