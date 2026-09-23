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
 * @param captureUnmappedColumns  read columns the mapping did not claim and hand them to
 *                                the handler. <b>False everywhere except the legacy
 *                                positional asset import.</b> On the mapping-driven
 *                                path an unmapped column is ignored, full stop: the
 *                                whole premise is that a customer brings a sheet from
 *                                another platform containing columns AssetIQ has no
 *                                field for, maps the ones that matter, and is not sent
 *                                away to edit their spreadsheet.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ImportOptions(
        DuplicateStrategy duplicateStrategy,
        boolean createMissingReferences,
        boolean dryRun,
        boolean skipInvalidRows,
        boolean captureUnmappedColumns
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

    /** Three-argument form: the wizard's defaults for everything not stated. */
    public ImportOptions(DuplicateStrategy duplicateStrategy, boolean createMissingReferences, boolean dryRun) {
        this(duplicateStrategy, createMissingReferences, dryRun, true, false);
    }

    /** Four-argument form for callers that predate {@code captureUnmappedColumns}. */
    public ImportOptions(DuplicateStrategy duplicateStrategy, boolean createMissingReferences,
                         boolean dryRun, boolean skipInvalidRows) {
        this(duplicateStrategy, createMissingReferences, dryRun, skipInvalidRows, false);
    }

    public static ImportOptions defaults() {
        return new ImportOptions(DuplicateStrategy.SKIP, false, false, true, false);
    }

    public ImportOptions withDryRun(boolean value) {
        return new ImportOptions(duplicateStrategy, createMissingReferences, value,
                skipInvalidRows, captureUnmappedColumns);
    }

    public ImportOptions withSkipInvalidRows(boolean value) {
        return new ImportOptions(duplicateStrategy, createMissingReferences, dryRun,
                value, captureUnmappedColumns);
    }

    public ImportOptions withCaptureUnmappedColumns(boolean value) {
        return new ImportOptions(duplicateStrategy, createMissingReferences, dryRun,
                skipInvalidRows, value);
    }
}
