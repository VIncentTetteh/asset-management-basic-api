package com.assetiq.imports;

import com.assetiq.models.Organisation;

import java.util.List;

/**
 * Everything the framework needs to know about one importable record type.
 *
 * <p>Adding a ninth entity type is exactly this: one implementation declaring its
 * descriptors and turning a mapped row into a record. Templates, auto-mapping,
 * preview, commit, presets, staging and tenant scoping are all already generic.</p>
 */
public interface ImportEntityHandler {

    ImportEntityType entityType();

    /** The single source of truth for this type. See {@link ImportFieldDescriptor}. */
    List<ImportFieldDescriptor> fields();

    /**
     * Whether this handler can turn a column into a custom field on the record it
     * writes. A capability, not a decision: the engine also requires either
     * {@link ImportOptions#customFieldColumns()} (the wizard, where the user picked the
     * columns) or {@link ImportOptions#captureUnmappedColumns()} (the legacy positional
     * asset import). Only assets have anywhere to put one, so only assets say true.
     */
    default boolean unmappedColumnsBecomeCustomFields() {
        return false;
    }

    /**
     * Open a run. The returned {@link ImportRunner} may hold per-run lookup caches;
     * it is used for one file and discarded, never shared between tenants.
     *
     * @param report where the run records what it did beyond writing rows — values it
     *               could not translate, records it created on the caller's behalf
     */
    ImportRunner runner(Organisation organisation, ImportOptions options, ImportRunReport report);

    /** Applies rows for one import run against one tenant. */
    interface ImportRunner {

        /**
         * Validate and (unless {@link ImportOptions#dryRun()}) persist one row.
         *
         * @throws FieldValidationException when one named cell is wrong
         * @throws IllegalArgumentException when the row as a whole is invalid
         * @throws IllegalStateException    when the row conflicts with stored data
         */
        RowOutcome apply(ImportRow row);
    }

    /** What happened to one row. */
    record RowOutcome(Kind kind, String message) {

        public enum Kind { CREATED, UPDATED, SKIPPED }

        public static RowOutcome created() { return new RowOutcome(Kind.CREATED, null); }
        public static RowOutcome updated() { return new RowOutcome(Kind.UPDATED, null); }
        public static RowOutcome skipped(String why) { return new RowOutcome(Kind.SKIPPED, why); }
    }
}
