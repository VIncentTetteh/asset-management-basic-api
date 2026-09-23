package com.assetiq.dto;

/**
 * One distinct raw value from an enum-typed column, and what the server thinks it means.
 *
 * @param value      the cell as the user wrote it, and the key to send back in
 *                   {@code valueMappings}
 * @param suggested  the constant to pre-select, or null when nothing matched confidently
 * @param exact      true when {@code value} is the constant itself, give or take case and
 *                   punctuation — the UI can show those as settled rather than asking
 * @param rowCount   how many rows in the sampled window carry this value, so the wizard
 *                   can put the values that matter at the top
 */
public record ImportValueSuggestionDto(
        String value,
        // Serialised even when null, overriding the application's global non-null
        // inclusion: "we have no suggestion" and "this key no longer exists" must not
        // look the same to the wizard.
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
        String suggested,
        boolean exact,
        int rowCount
) {}
