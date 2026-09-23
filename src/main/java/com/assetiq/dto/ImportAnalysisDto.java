package com.assetiq.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The result of analysing an upload: what is in the file, and the framework's best
 * guess at how it lines up with the target type's fields.
 *
 * @param uploadId              handle for the staged file; preview and commit take it
 * @param detectedColumns       every column in the file, with sample values
 * @param suggestedMapping      field name to column index, listing <em>every</em> field
 *                              of the type so the wizard can render its whole form from
 *                              this one payload. A null value means "we are not
 *                              confident, ask the user" — never a silent guess. The
 *                              nulls are serialised explicitly, overriding this
 *                              application's global non-null inclusion, because an
 *                              absent key and a null one would otherwise be
 *                              indistinguishable from a field that no longer exists.
 * @param rowCount              data rows found (excluding the header)
 * @param unmappedColumns       header texts no field claimed
 * @param missingRequiredFields required fields with no suggested column; the wizard
 *                              must not allow a commit while this is non-empty
 * @param truncated             true when the file was longer than the row ceiling
 * @param expiresAt             when the staged upload will be deleted, ISO-8601
 */
public record ImportAnalysisDto(
        UUID uploadId,
        List<ImportDetectedColumnDto> detectedColumns,
        @JsonInclude(content = JsonInclude.Include.ALWAYS)
        Map<String, Integer> suggestedMapping,
        int rowCount,
        List<String> unmappedColumns,
        List<String> missingRequiredFields,
        boolean truncated,
        String expiresAt
) {}
