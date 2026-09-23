package com.assetiq.imports;

import java.util.UUID;

/**
 * The shape every entity handler's per-run worker shares: build a payload from the
 * mapped row, decide whether it duplicates something already stored, then create,
 * update, skip or fail according to the run's {@link ImportOptions}.
 *
 * <p>Keeping the duplicate decision here rather than in each handler is what makes the
 * duplicate option mean the same thing for all eight types — and what keeps a ninth
 * type from having to reimplement it.</p>
 *
 * @param <P> the handler's payload type, normally the entity's DTO
 */
public abstract class AbstractImportRunner<P> implements ImportEntityHandler.ImportRunner {

    protected final ImportOptions options;

    protected AbstractImportRunner(ImportOptions options) {
        this.options = options;
    }

    @Override
    public final ImportEntityHandler.RowOutcome apply(ImportRow row) {
        P payload = build(row);
        UUID existing = findExisting(row, payload);

        if (existing != null) {
            return switch (options.duplicateStrategy()) {
                case SKIP -> ImportEntityHandler.RowOutcome.skipped(null);
                case FAIL -> throw new IllegalStateException(
                        "A matching " + describe() + " already exists (" + naturalKey(row, payload) + ")."
                                + " Choose 'update existing' or 'skip duplicates' to import this file.");
                case UPDATE -> {
                    if (!options.dryRun()) update(existing, payload);
                    yield ImportEntityHandler.RowOutcome.updated();
                }
            };
        }

        beforeCreate(payload);
        if (!options.dryRun()) create(payload);
        return ImportEntityHandler.RowOutcome.created();
    }

    /**
     * Checks that must run whether or not the row will actually be written — a plan
     * limit, say. A dry run that skipped these would tell the user their file is fine
     * and then fail on commit, which is the exact outcome preview exists to prevent.
     */
    protected void beforeCreate(P payload) {
        // no-op by default
    }

    /** Parse and validate the row into a payload. Throws for a bad cell. */
    protected abstract P build(ImportRow row);

    /** The id of an existing record this row duplicates, or null. */
    protected abstract UUID findExisting(ImportRow row, P payload);

    protected abstract void create(P payload);

    protected abstract void update(UUID id, P payload);

    /** Singular, lower-case, for the duplicate message: "supplier", "software licence". */
    protected abstract String describe();

    /** The value that made this a duplicate, quoted back at the user. */
    protected abstract String naturalKey(ImportRow row, P payload);
}
