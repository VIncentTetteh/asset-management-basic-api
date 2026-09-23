package com.assetiq.imports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One data row, already resolved through the column mapping.
 *
 * <p>Handlers ask for fields by name and never see a column index — that is the whole
 * reason a customer's arbitrary column order can be imported at all. Every accessor
 * that can fail throws {@link FieldValidationException} naming the field, which the
 * engine turns into an error naming the user's own header text.</p>
 *
 * <h2>Which cells can fail a row, and which cannot</h2>
 * <p>A required field left blank, a number that is not a number and a date nobody can
 * parse are still row errors: those are the user telling us something is missing or
 * malformed, and silently writing a null would be worse than a clear refusal.</p>
 *
 * <p><b>Unrecognised enum vocabulary is not.</b> A sheet that says {@code Laptop} where
 * AssetIQ says {@code HARDWARE} is not malformed, it is a different vocabulary, and the
 * answer is a translation the user can make in the wizard — not a rejected file. So an
 * enum cell is resolved through, in order: the value mappings the user chose, the
 * constant itself, {@link ImportEnumAliases}. If none of those land, the field is left
 * blank and a note says so. The row still imports. The only exception is a required enum
 * field, where blank is not a legal answer and the row does fail.</p>
 */
public final class ImportRow {

    /**
     * Date formats accepted in addition to a real Excel date cell. ISO first; the rest
     * are what the common exports actually emit. Ambiguous {@code 01/02/2024} is read
     * day-first, matching the ISO-adjacent conventions of the markets AssetIQ serves,
     * and the template says so.
     */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT),
            DateTimeFormatter.ofPattern("dd-MM-uuuu", Locale.ROOT),
            DateTimeFormatter.ofPattern("uuuu/MM/dd", Locale.ROOT),
            DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ROOT),
            DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ROOT)
    );

    private static final List<String> TRUE_VALUES = List.of("true", "yes", "y", "1", "on");
    private static final List<String> FALSE_VALUES = List.of("false", "no", "n", "0", "off");

    private final int rowNumber;
    private final Map<String, String> values;
    private final Map<String, String> headers;
    private final Map<String, String> unmapped;
    private final ImportValueMappings valueMappings;
    private final Set<String> requiredFields;
    private final ImportRunReport report;

    public ImportRow(int rowNumber,
                     Map<String, String> values,
                     Map<String, String> headers,
                     Map<String, String> unmapped) {
        this(rowNumber, values, headers, unmapped,
                ImportValueMappings.empty(), Set.of(), new ImportRunReport());
    }

    public ImportRow(int rowNumber,
                     Map<String, String> values,
                     Map<String, String> headers,
                     Map<String, String> unmapped,
                     ImportValueMappings valueMappings,
                     Set<String> requiredFields,
                     ImportRunReport report) {
        this.rowNumber = rowNumber;
        this.values = values;
        this.headers = headers;
        this.unmapped = unmapped == null ? Map.of() : new LinkedHashMap<>(unmapped);
        this.valueMappings = valueMappings == null ? ImportValueMappings.empty() : valueMappings;
        this.requiredFields = requiredFields == null ? Set.of() : Set.copyOf(requiredFields);
        this.report = report == null ? new ImportRunReport() : report;
    }

    /** 1-based row number as the spreadsheet shows it (the header row is row 1). */
    public int rowNumber() { return rowNumber; }

    /** Columns the mapping did not claim, keyed by the user's own header text. */
    public Map<String, String> unmapped() { return unmapped; }

    /** The user's header text for a field, or the field name when it was not mapped. */
    public String headerFor(String field) {
        return headers.getOrDefault(field, field);
    }

    /** True when every mapped cell in this row is blank. */
    public boolean isBlank() {
        return values.values().stream().allMatch(v -> v == null || v.isBlank())
                && unmapped.values().stream().allMatch(v -> v == null || v.isBlank());
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String string(String field) {
        String raw = values.get(field);
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public String requiredString(String field) {
        String value = string(field);
        if (value == null) {
            throw new FieldValidationException(field, "is required but was blank");
        }
        return value;
    }

    public Integer integer(String field) {
        String raw = string(field);
        if (raw == null) return null;
        try {
            return new BigDecimal(raw.replace(",", "")).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new FieldValidationException(field, "expected a whole number but found '" + raw + "'");
        }
    }

    public BigDecimal decimal(String field) {
        String raw = string(field);
        if (raw == null) return null;
        String cleaned = raw.replace(",", "").replace(" ", "");
        // Currency symbols are common in exported cost columns; strip a leading one
        // rather than rejecting a row over cosmetics.
        cleaned = cleaned.replaceAll("^[^0-9+\\-.]+", "");
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new FieldValidationException(field, "expected a number but found '" + raw + "'");
        }
    }

    public LocalDate date(String field) {
        String raw = string(field);
        if (raw == null) return null;
        // Excel serial dates arrive already formatted by the reader; a bare number here
        // is therefore a genuine ambiguity, so it is rejected rather than guessed at.
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw, format);
            } catch (Exception ignored) {
                // try the next format
            }
        }
        throw new FieldValidationException(field,
                "expected a date as YYYY-MM-DD but found '" + raw + "'");
    }

    public Boolean bool(String field) {
        String raw = string(field);
        if (raw == null) return null;
        String lower = raw.toLowerCase(Locale.ROOT);
        if (TRUE_VALUES.contains(lower)) return Boolean.TRUE;
        if (FALSE_VALUES.contains(lower)) return Boolean.FALSE;
        throw new FieldValidationException(field,
                "expected yes or no but found '" + raw + "'");
    }

    /**
     * An enum cell, read leniently. See the class comment for why.
     *
     * @return the constant, or null when the cell is blank, was mapped to
     *         {@link ImportValueMappings#IGNORE}, or means nothing we recognise
     * @throws FieldValidationException only when the field is required and the answer
     *                                  came out blank
     */
    public <E extends Enum<E>> E enumValue(Class<E> type, String field) {
        String raw = string(field);
        if (raw == null) {
            return requireNonBlankEnum(type, field, null, null);
        }

        Optional<String> chosen = valueMappings.target(field, raw);
        if (chosen.isPresent()) {
            String target = chosen.get();
            if (ImportValueMappings.IGNORE.equalsIgnoreCase(target)) {
                note(field, raw, "'" + raw + "' was set to be ignored, so this field was left blank.");
                return requireNonBlankEnum(type, field, raw, "you chose to ignore it");
            }
            try {
                return Enum.valueOf(type, target.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_"));
            } catch (IllegalArgumentException badTarget) {
                // The client sent a target this enum does not have. That is a client bug,
                // not the user's spreadsheet, and it must not cost them the row.
                note(field, raw, "'" + raw + "' was mapped to '" + target
                        + "', which is not a value this field accepts, so it was left blank.");
                return requireNonBlankEnum(type, field, raw, "'" + target + "' is not an accepted value");
            }
        }

        Optional<E> resolved = ImportEnumAliases.resolve(type, raw);
        if (resolved.isPresent()) {
            return resolved.get();
        }

        note(field, raw, "'" + raw + "' is not a value this field recognises, so it was left blank."
                + " Map it to one of " + allowed(type) + " to keep it.");
        return requireNonBlankEnum(type, field, raw, "'" + raw + "' is not one of " + allowed(type));
    }

    /**
     * Blank is fine for an optional enum and fatal for a required one. Splitting it out
     * keeps the four exits above from each repeating the rule.
     */
    private <E extends Enum<E>> E requireNonBlankEnum(Class<E> type, String field, String raw, String why) {
        if (!requiredFields.contains(field)) {
            return null;
        }
        if (raw == null) {
            throw new FieldValidationException(field,
                    "is required but was blank. Allowed: " + allowed(type));
        }
        throw new FieldValidationException(field,
                "is required, and " + why + ". Allowed: " + allowed(type));
    }

    /** Records a leniency against this row, for the result's notes list. */
    private void note(String field, String value, String message) {
        report.note(field, headerFor(field), value, message);
    }

    private static <E extends Enum<E>> String allowed(Class<E> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
