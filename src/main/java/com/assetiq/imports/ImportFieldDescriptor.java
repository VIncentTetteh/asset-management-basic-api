package com.assetiq.imports;

import java.util.List;
import java.util.Locale;

/**
 * One importable field, declared once.
 *
 * <p>Everything the import framework shows or enforces about a column derives from this
 * record: the downloadable template's header row, its example row and its documentation
 * sheet; the auto-suggested mapping (via {@link #aliases()}); the row parser's type
 * handling; and the error text a user sees when a cell is wrong.</p>
 *
 * <p>That single-source property is the whole point. A template generated from a
 * different list than the importer validates against is worse than no template at all:
 * it teaches the customer a format the product then rejects.</p>
 *
 * @param name       internal field name, stable, used as the mapping key in the API
 * @param label      human label, used as the template header
 * @param required   whether a row is rejected when this is blank
 * @param dataType   how the cell is read and what the wizard shows
 * @param enumValues allowed values when {@code dataType == ENUM}; empty otherwise
 * @param example    a realistic value for the template's example row
 * @param notes      format notes: date format, units, resolution rules
 * @param aliases    header names other platforms use for this field, for auto-matching
 */
public record ImportFieldDescriptor(
        String name,
        String label,
        boolean required,
        ImportDataType dataType,
        List<String> enumValues,
        String example,
        String notes,
        List<String> aliases
) {

    public ImportFieldDescriptor {
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }

    // ── Factories ─────────────────────────────────────────────────────────────
    // Positional constructors for eight entity types would be unreadable, so each
    // descriptor set is written with these.

    public static Builder field(String name, String label, ImportDataType dataType) {
        return new Builder(name, label, dataType);
    }

    public static <E extends Enum<E>> Builder enumField(String name, String label, Class<E> type) {
        return new Builder(name, label, ImportDataType.ENUM)
                .values(java.util.Arrays.stream(type.getEnumConstants()).map(Enum::name).toList());
    }

    /**
     * The canonical comparison form of a header: lower-cased, punctuation and
     * whitespace removed. {@code "Asset Tag"}, {@code "asset_tag"} and
     * {@code "ASSET-TAG"} all collapse to {@code "assettag"}.
     */
    public static String normalise(String header) {
        if (header == null) return "";
        return header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** All normalised forms this field will auto-match: its name, label and aliases. */
    public List<String> matchKeys() {
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        keys.add(normalise(name));
        keys.add(normalise(label));
        aliases.forEach(a -> keys.add(normalise(a)));
        keys.remove("");
        return List.copyOf(keys);
    }

    public static final class Builder {
        private final String name;
        private final String label;
        private final ImportDataType dataType;
        private boolean required;
        private List<String> enumValues = List.of();
        private String example = "";
        private String notes = "";
        private List<String> aliases = List.of();

        private Builder(String name, String label, ImportDataType dataType) {
            this.name = name;
            this.label = label;
            this.dataType = dataType;
        }

        public Builder required() { this.required = true; return this; }
        public Builder values(List<String> values) { this.enumValues = values; return this; }
        public Builder example(String value) { this.example = value; return this; }
        public Builder notes(String value) { this.notes = value; return this; }
        public Builder aliases(String... values) { this.aliases = List.of(values); return this; }

        public ImportFieldDescriptor build() {
            return new ImportFieldDescriptor(name, label, required, dataType, enumValues, example, notes, aliases);
        }
    }
}
