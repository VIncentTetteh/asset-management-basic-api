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
     * Whether this handler can consume columns the mapping did not claim, as custom
     * fields. A capability, not a decision: the engine also requires
     * {@link ImportOptions#captureUnmappedColumns()}, which only the legacy positional
     * asset import sets. On the mapping-driven path an unmapped column is ignored.
     */
    default boolean unmappedColumnsBecomeCustomFields() {
        return false;
    }

    /**
     * Open a run. The returned {@link ImportRunner} may hold per-run lookup caches;
     * it is used for one file and discarded, never shared between tenants.
     */
    ImportRunner runner(Organisation organisation, ImportOptions options);

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
