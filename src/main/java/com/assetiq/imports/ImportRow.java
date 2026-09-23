package com.assetiq.imports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One data row, already resolved through the column mapping.
 *
 * <p>Handlers ask for fields by name and never see a column index — that is the whole
 * reason a customer's arbitrary column order can be imported at all. Every accessor
 * that can fail throws {@link FieldValidationException} naming the field, which the
 * engine turns into an error naming the user's own header text.</p>
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

    public ImportRow(int rowNumber,
                     Map<String, String> values,
                     Map<String, String> headers,
                     Map<String, String> unmapped) {
        this.rowNumber = rowNumber;
        this.values = values;
        this.headers = headers;
        this.unmapped = unmapped == null ? Map.of() : new LinkedHashMap<>(unmapped);
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

    public <E extends Enum<E>> E enumValue(Class<E> type, String field) {
        String raw = string(field);
        if (raw == null) return null;
        String candidate = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        try {
            return Enum.valueOf(type, candidate);
        } catch (IllegalArgumentException e) {
            throw new FieldValidationException(field,
                    "'" + raw + "' is not a valid value. Allowed: "
                            + Arrays.stream(type.getEnumConstants()).map(Enum::name)
                            .collect(Collectors.joining(", ")));
        }
    }
}
