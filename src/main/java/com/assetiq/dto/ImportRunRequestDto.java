package com.assetiq.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

/** The body of both preview and commit: which staged file, mapped how, run how. */
@Data
public class ImportRunRequestDto {

    @NotNull(message = "uploadId is required")
    private UUID uploadId;

    /**
     * Field name to 0-based column index. A null value, or an absent field, means the
     * column is not mapped. Sent whole rather than as a diff, so what the user saw in
     * the wizard is exactly what runs.
     */
    private Map<String, Integer> mapping;

    /**
     * Duplicate handling, reference creation, the columns to keep as custom fields and
     * the value translations the user chose. Preview and commit take the identical
     * structure and run it identically — that is the whole reason they cannot disagree.
     */
    private ImportOptionsDto options;
}
