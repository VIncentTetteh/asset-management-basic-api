package com.assetiq.imports;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The knobs an import run exposes. Deliberately few, and all of them actually enforced
 * and tested — an option the engine advertises but half-honours is worse than one that
 * does not exist.
 *
 * @param duplicateStrategy       what to do when a row matches an existing record
 * @param createMissingReferences create a referenced record (a category, a location) that
 *                                the sheet names but the tenant does not have. <b>The
 *                                wizard defaults this on</b>: someone migrating from
 *                                another platform has their own category and department
 *                                lists, and sending them away to key those in by hand
 *                                before their data will load is the failure this import
 *                                exists to remove. Bounded by
 *                                {@link #MAX_CREATED_PER_REFERENCE_TYPE}, reported by
 *                                name, and still switchable off.
 * @param dryRun                  validate everything, write nothing
 * @param skipInvalidRows         keep going past a bad row (the default and the normal
 *                                case) or stop at the first one. Stopping does not undo
 *                                the rows already written — see {@link ImportEngine}
 *                                for why an import is best-effort rather than
 *                                all-or-nothing.
 * @param captureUnmappedColumns  read <em>every</em> column the mapping did not claim and
 *                                hand it to the handler as a custom field. True only on
 *                                the legacy positional asset import, where extra columns
 *                                past the fixed layout have always behaved this way. The
 *                                wizard names the columns it wants instead, in
 *                                {@code customFieldColumns}, because "everything I did
 *                                not map" and "the three columns I chose to keep" are
 *                                different requests.
 * @param customFieldColumns      0-based indices of columns the user asked to keep as
 *                                custom fields. Anything neither mapped to a field nor
 *                                named here is ignored, which is what the user chose when
 *                                they left a column on "ignore".
 * @param valueMappings           field → raw cell value → target enum constant, or
 *                                {@link ImportValueMappings#IGNORE}. See
 *                                {@link ImportValueMappings}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ImportOptions(
        DuplicateStrategy duplicateStrategy,
        boolean createMissingReferences,
        boolean dryRun,
        boolean skipInvalidRows,
        boolean captureUnmappedColumns,
        Set<Integer> customFieldColumns,
        Map<String, Map<String, String>> valueMappings
) {

    /**
     * How many records of one reference type a single file may create.
     *
     * <p>Creating references from a spreadsheet is a write amplifier: one column of
     * free text becomes rows in someone's department list, and a file with a typo per
     * row would otherwise quietly manufacture three thousand departments. The ceiling is
     * per type per run, generous enough for a real migration — nobody has 200 genuine
     * departments they forgot to mention — and tight enough that a runaway file stops
     * and says so rather than being discovered a week later.</p>
     */
    public static final int MAX_CREATED_PER_REFERENCE_TYPE = 200;

    /**
     * How many columns one file may turn into custom fields.
     *
     * <p>Same reasoning one level up: a custom field is a schema change for the tenant,
     * not a cell. A sheet needing more than this many extra columns is telling us we are
     * missing a real field, not that the tenant wants thirty.</p>
     */
    public static final int MAX_CUSTOM_FIELD_COLUMNS = 30;

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
        customFieldColumns = customFieldColumns == null
                ? Set.of()
                : Set.copyOf(new LinkedHashSet<>(customFieldColumns));
        valueMappings = valueMappings == null ? Map.of() : Map.copyOf(valueMappings);
    }

    /** Three-argument form: the wizard's defaults for everything not stated. */
    public ImportOptions(DuplicateStrategy duplicateStrategy, boolean createMissingReferences, boolean dryRun) {
        this(duplicateStrategy, createMissingReferences, dryRun, true, false, Set.of(), Map.of());
    }

    /** Four-argument form for callers that predate {@code captureUnmappedColumns}. */
    public ImportOptions(DuplicateStrategy duplicateStrategy, boolean createMissingReferences,
                         boolean dryRun, boolean skipInvalidRows) {
        this(duplicateStrategy, createMissingReferences, dryRun, skipInvalidRows, false, Set.of(), Map.of());
    }

    /** Five-argument form for callers that predate the wizard's column and value choices. */
    public ImportOptions(DuplicateStrategy duplicateStrategy, boolean createMissingReferences,
                         boolean dryRun, boolean skipInvalidRows, boolean captureUnmappedColumns) {
        this(duplicateStrategy, createMissingReferences, dryRun, skipInvalidRows,
                captureUnmappedColumns, Set.of(), Map.of());
    }

    /**
     * The safe defaults for a caller that says nothing: skip duplicates, do not invent
     * referenced records, write for real. Note that the <em>wizard</em> does not use
     * these — see {@link #wizardDefaults()} — because a wizard user has just been shown
     * exactly what will be created.
     */
    public static ImportOptions defaults() {
        return new ImportOptions(DuplicateStrategy.SKIP, false, false, true, false, Set.of(), Map.of());
    }

    /** What the wizard runs when the caller sends no options at all. */
    public static ImportOptions wizardDefaults() {
        return new ImportOptions(DuplicateStrategy.SKIP, true, false, true, false, Set.of(), Map.of());
    }

    public ImportOptions withDryRun(boolean value) {
        return new ImportOptions(duplicateStrategy, createMissingReferences, value,
                skipInvalidRows, captureUnmappedColumns, customFieldColumns, valueMappings);
    }

    public ImportOptions withSkipInvalidRows(boolean value) {
        return new ImportOptions(duplicateStrategy, createMissingReferences, dryRun,
                value, captureUnmappedColumns, customFieldColumns, valueMappings);
    }

    public ImportOptions withCaptureUnmappedColumns(boolean value) {
        return new ImportOptions(duplicateStrategy, createMissingReferences, dryRun,
                skipInvalidRows, value, customFieldColumns, valueMappings);
    }

    public ImportOptions withCustomFieldColumns(Set<Integer> value) {
        return new ImportOptions(duplicateStrategy, createMissingReferences, dryRun,
                skipInvalidRows, captureUnmappedColumns, value, valueMappings);
    }

    public ImportOptions withValueMappings(Map<String, Map<String, String>> value) {
        return new ImportOptions(duplicateStrategy, createMissingReferences, dryRun,
                skipInvalidRows, captureUnmappedColumns, customFieldColumns, value);
    }

    /** The value mappings as a lookup, built once per run by the engine. */
    public ImportValueMappings valueMappingIndex() {
        return ImportValueMappings.of(valueMappings);
    }
}
