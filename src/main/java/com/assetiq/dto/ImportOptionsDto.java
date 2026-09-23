package com.assetiq.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Import options as the API accepts them. All optional; omitted means the wizard's
 * default, which is not the same as the strictest possible setting.
 */
@Data
public class ImportOptionsDto {

    /** SKIP, UPDATE or FAIL. Defaults to SKIP. */
    private String duplicateStrategy;

    /**
     * Create a category, location, supplier or department the sheet names but the tenant
     * does not have.
     *
     * <p><b>Defaults to true on the wizard path.</b> Someone migrating from another
     * platform arrives with their own category and department lists, and the old default
     * sent them away to key those in by hand before their file would load — while
     * offering the fix in an option the wizard never showed them. Creation is bounded per
     * type per file and every created record is named in the result, so this is a
     * convenience with a receipt rather than a silent write.</p>
     *
     * <p>Send false explicitly for a strict run: an unknown name then fails its row, as
     * it always did.</p>
     */
    private Boolean createMissingReferences;

    /** Validate and report without writing anything. Defaults to false. */
    private Boolean dryRun;

    /**
     * Keep going past a row that fails validation. Defaults to true, which is what a
     * migration wants: a handful of bad rows out of 3000 should not stop the other
     * 2990. False stops at the first bad row -- it does not undo the rows already
     * written, because an import is best-effort with a report, not a single
     * transaction.
     */
    private Boolean skipInvalidRows;

    /**
     * 0-based indices of columns the user chose to keep as custom fields rather than
     * ignore. A column that is neither mapped to a field nor listed here is not read.
     *
     * <p>Only accepted for record types that can store one, and only for a tenant with
     * the custom-fields feature — {@link ImportAnalysisDto#customFieldsAvailable()} says
     * which, so the wizard never offers an option that would be refused.</p>
     */
    private List<Integer> customFieldColumns;

    /**
     * What the user decided each unfamiliar value means:
     * {@code {"assetType": {"Laptop": "HARDWARE", "Sundry": "__IGNORE__"}}}.
     *
     * <p>Keys are field names; inner keys are the raw cell values as they appear in the
     * file, matched case- and punctuation-insensitively. A value with no entry here is
     * not an error — it falls back to the built-in alias table and then to leaving the
     * field blank with a note.</p>
     */
    private Map<String, Map<String, String>> valueMappings;
}
