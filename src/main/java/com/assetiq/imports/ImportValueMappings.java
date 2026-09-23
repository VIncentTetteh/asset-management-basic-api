package com.assetiq.imports;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The user's own answer to "what does this word in my spreadsheet mean here?".
 *
 * <p>The wizard shows, per enum-typed field, every distinct raw value the file carries
 * and a dropdown of the allowed constants. Whatever the user picks arrives as
 * {@code field → raw value → constant}, or the literal {@link #IGNORE} for a value they
 * would rather drop than translate. Lookup is on the raw value's normalised form, so
 * {@code "Laptop"}, {@code "laptop"} and {@code "LAPTOP "} are one entry rather than
 * three the user has to answer separately.</p>
 *
 * <p>An unmapped value is not an error here or anywhere downstream — see
 * {@link ImportRow#enumValue}. This structure only carries decisions that were
 * <em>made</em>; the absence of one is handled by falling back to
 * {@link ImportEnumAliases} and then to leaving the field blank.</p>
 */
public final class ImportValueMappings {

    /** Target that means "drop this value, leave the field blank, say so in a note". */
    public static final String IGNORE = "__IGNORE__";

    private static final ImportValueMappings EMPTY = new ImportValueMappings(Map.of());

    /** field name → normalised raw value → target constant (or {@link #IGNORE}). */
    private final Map<String, Map<String, String>> index;

    private ImportValueMappings(Map<String, Map<String, String>> index) {
        this.index = index;
    }

    public static ImportValueMappings empty() {
        return EMPTY;
    }

    /** Builds the lookup index from the wire shape, tolerating nulls throughout. */
    public static ImportValueMappings of(Map<String, Map<String, String>> raw) {
        if (raw == null || raw.isEmpty()) return EMPTY;
        Map<String, Map<String, String>> index = new LinkedHashMap<>();
        raw.forEach((field, values) -> {
            if (field == null || field.isBlank() || values == null || values.isEmpty()) return;
            Map<String, String> byValue = new LinkedHashMap<>();
            values.forEach((rawValue, target) -> {
                String key = ImportEnumAliases.normalise(rawValue);
                if (key.isEmpty() || target == null || target.isBlank()) return;
                byValue.put(key, target.trim());
            });
            if (!byValue.isEmpty()) index.put(field.trim(), Map.copyOf(byValue));
        });
        return index.isEmpty() ? EMPTY : new ImportValueMappings(Map.copyOf(index));
    }

    public boolean isEmpty() {
        return index.isEmpty();
    }

    /**
     * The target the user chose for this cell, if any.
     *
     * @return the constant name, {@link #IGNORE}, or empty when the user said nothing
     *         about this value
     */
    public Optional<String> target(String field, String rawValue) {
        Map<String, String> byValue = index.get(field);
        if (byValue == null) return Optional.empty();
        return Optional.ofNullable(byValue.get(ImportEnumAliases.normalise(rawValue)));
    }

    /** True when the user asked for this value to be dropped rather than translated. */
    public boolean isIgnored(String field, String rawValue) {
        return target(field, rawValue).map(IGNORE::equalsIgnoreCase).orElse(false);
    }
}
