package com.assetiq.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The result of analysing an upload: what is in the file, and the framework's best
 * guess at how it lines up with the target type's fields.
 *
 * @param uploadId              handle for the staged file; preview and commit take it
 * @param detectedColumns       every column in the file, with sample values
 * @param suggestedMapping      field name to column index; a null value means "we are
 *                              not confident, ask the user" — never a silent guess
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
        Map<String, Integer> suggestedMapping,
        int rowCount,
        List<String> unmappedColumns,
        List<String> missingRequiredFields,
        boolean truncated,
        String expiresAt
) {}
