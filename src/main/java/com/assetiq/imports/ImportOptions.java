package com.assetiq.imports;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The knobs an import run exposes. Deliberately few, and all of them actually enforced
 * and tested — an option the engine advertises but half-honours is worse than one that
 * does not exist.
 *
 * @param duplicateStrategy       what to do when a row matches an existing record
 * @param createMissingReferences create a referenced record (a category, a location)
 *                                that the sheet names but the tenant does not have
 * @param dryRun                  validate everything, write nothing
 * @param skipInvalidRows         keep going past a bad row (the default and the normal
 *                                case) or stop at the first one. Stopping does not undo
 *                                the rows already written — see {@link ImportEngine}
 *                                for why an import is best-effort rather than
 *                                all-or-nothing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ImportOptions(
        DuplicateStrategy duplicateStrategy,
        boolean createMissingReferences,
        boolean dryRun,
        boolean skipInvalidRows
) {

    public enum DuplicateStrategy {
        /** Leave the existing record alone and count the row as skipped. */
        SKIP,
        /** Patch the existing record with the non-blank values in the row. */
        UPDATE,
        /** Report the row as an error and import nothing for it. */
        FAIL
    }

    public ImportOptions {
        if (duplicateStrategy == null) duplicateStrategy = DuplicateStrategy.SKIP;
    }

    /** Three-argument form for callers that predate {@code skipInvalidRows}. */
    public ImportOptions(DuplicateStrategy duplicateStrategy, boolean createMissingReferences, boolean dryRun) {
        this(duplicateStrategy, createMissingReferences, dryRun, true);
    }

    public static ImportOptions defaults() {
        return new ImportOptions(DuplicateStrategy.SKIP, false, false, true);
    }

    public ImportOptions withDryRun(boolean value) {
        return new ImportOptions(duplicateStrategy, createMissingReferences, value, skipInvalidRows);
    }

    public ImportOptions withSkipInvalidRows(boolean value) {
        return new ImportOptions(duplicateStrategy, createMissingReferences, dryRun, value);
    }
}
