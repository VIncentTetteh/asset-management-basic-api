package com.assetiq.dto;

import java.util.List;

/**
 * Everything the wizard needs to render a dropdown for one enum-typed field, and to
 * pre-fill it.
 *
 * <p>This exists because the old behaviour was to fail the row: a file saying
 * {@code Asset type = "Laptop"} was refused with a list of six constants and no way for
 * the user to say that Laptop means HARDWARE. The list is now data the UI can turn into
 * a per-value dropdown, with the server's best guess already selected and the user free
 * to change or ignore any of it.</p>
 *
 * @param field         internal field name — the key to use in {@code valueMappings}
 * @param label         human label, as the mapping step shows it
 * @param column        0-based index of the column this field is currently mapped to
 * @param header        that column's header, as the user wrote it
 * @param allowedValues every constant this field accepts, in declaration order
 * @param values        the distinct raw values found in the file, with a suggestion each
 */
public record ImportEnumFieldDto(
        String field,
        String label,
        Integer column,
        String header,
        List<String> allowedValues,
        List<ImportValueSuggestionDto> values
) {}
