package com.assetiq.dto;

import lombok.Data;

/**
 * Import options as the API accepts them. All three are optional; omitted means the
 * safe default (skip duplicates, do not invent referenced records, write for real).
 */
@Data
public class ImportOptionsDto {

    /** SKIP, UPDATE or FAIL. Defaults to SKIP. */
    private String duplicateStrategy;

    /** Create a category, location, supplier or department the sheet names but the
     *  tenant does not have. Defaults to false. */
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
}
