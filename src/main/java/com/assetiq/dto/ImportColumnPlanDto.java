package com.assetiq.dto;

/**
 * What the wizard proposes to do with one column of the uploaded file.
 *
 * <p>Three outcomes, and the user may change any of them: feed a known field, become a
 * custom field on the records this import creates, or be ignored. Before this, a column
 * AssetIQ had no field for was simply dropped without telling anyone — which is safe but
 * loses exactly the columns a customer migrating off a spreadsheet cares about.</p>
 *
 * @param index        0-based position in the file
 * @param header       the header text as the user wrote it
 * @param action       {@code FIELD}, {@code CUSTOM_FIELD} or {@code IGNORE}
 * @param field        the field it feeds when {@code action} is {@code FIELD}
 * @param customFieldName the name a custom field would be created under, sanitised and
 *                     length-capped, when {@code CUSTOM_FIELD} is available for it
 * @param inferredType the shape of the sampled values: STRING, INTEGER, DECIMAL, DATE
 *                     or BOOLEAN. Advisory only
 * @param canBeCustomField false when this file's type has nowhere to put a custom field,
 *                     or the tenant does not have them enabled; the UI must then offer
 *                     only {@code IGNORE} rather than an option that would fail
 */
public record ImportColumnPlanDto(
        int index,
        String header,
        String action,
        String field,
        String customFieldName,
        String inferredType,
        boolean canBeCustomField
) {
    public static final String FIELD = "FIELD";
    public static final String CUSTOM_FIELD = "CUSTOM_FIELD";
    public static final String IGNORE = "IGNORE";
}
