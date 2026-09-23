package com.assetiq.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The result of analysing an upload: what is in the file, and the framework's best
 * guess at how it lines up with the target type's fields.
 *
 * <p>The wizard renders its whole mapping step from this one payload — the column list,
 * the field dropdowns, the per-value dropdowns for enum columns, and what will happen to
 * the columns nothing claimed. Nothing here is binding: every suggestion is a default the
 * user may change, and the run is driven by what they send back, not by what was
 * suggested.</p>
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
 * @param enumFields            per enum-typed mapped field: the allowed constants, the
 *                              distinct raw values in the file, and a suggestion for
 *                              each. The UI renders a dropdown per value; whatever the
 *                              user settles on comes back as {@code valueMappings}. An
 *                              unmapped value is not an error at any point — it leaves
 *                              the field blank and earns a note
 * @param columnPlan            what is proposed for each column: feed a field, become a
 *                              custom field, or be ignored
 * @param customFieldsAvailable whether "create as a custom field" may be offered at all.
 *                              False when this record type has nowhere to store one, or
 *                              the tenant does not have the custom-fields feature; the
 *                              wizard must then offer only "ignore"
 * @param customFieldsUnavailableReason why, in words the UI can show, when the above is
 *                              false
 * @param createMissingReferencesDefault what the wizard should have ticked for "create
 *                              missing referenced records". On for the reference types
 *                              that are safe to create by name
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
        String expiresAt,
        List<ImportEnumFieldDto> enumFields,
        List<ImportColumnPlanDto> columnPlan,
        boolean customFieldsAvailable,
        String customFieldsUnavailableReason,
        boolean createMissingReferencesDefault
) {}
