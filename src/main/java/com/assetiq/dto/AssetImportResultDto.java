package com.assetiq.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The outcome of an import run.
 *
 * <p>Named for assets for historical reasons; it is the result shape for every entity
 * type the generic import framework handles.</p>
 *
 * <h2>The counts, and why they are what they are</h2>
 * <p>Two invariants hold on every result, and there are tests that say so:</p>
 * <ol>
 *   <li>{@code totalRows == imported + updated + skipped} — every row read is accounted
 *       for exactly once;</li>
 *   <li>{@code errors.size() == failed} unless {@link #errorsTruncated} is set — an error
 *       list longer than the failure count is how a screen ends up claiming "1 row" and
 *       "2 rows were not imported" in the same breath.</li>
 * </ol>
 * <p>{@code skipped} is every row that was not written, of which {@code failed} is the
 * part that went wrong and {@code duplicatesSkipped} the part that was deliberately left
 * alone. Whole-file problems live in {@link #fatalError} and early stops in
 * {@link #stoppedReason}; neither is an entry in {@code errors}, because a synthetic
 * row 0 is not a row and inflates the failure count.</p>
 *
 * <h2>The outcome</h2>
 * <p>{@link #outcome} exists so the UI cannot put a green tick over "imported 0 rows".
 * A run that wrote nothing is not a success, and the server says which of success,
 * partial success and failure it was rather than leaving the client to infer it from
 * four integers.</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssetImportResultDto {

    /** Rows read from the file, excluding the header and blank lines. */
    private int totalRows;

    /** Rows that created a new record. */
    private int imported;

    /** Rows that patched an existing record. */
    private int updated;

    /** Rows that were not written, for any reason: {@code failed + duplicatesSkipped}. */
    private int skipped;

    /** Rows that were not written because something was wrong. Matches {@code errors}. */
    private int failed;

    /** Rows that were not written because they already existed and the run said skip. */
    private int duplicatesSkipped;

    private boolean dryRun;

    /** What the UI should render: a success, a partial success, or a failure. */
    private Outcome outcome = Outcome.NOTHING_TO_IMPORT;

    /** One error per failed row. Never a whole-file or whole-mapping problem. */
    private List<RowError> errors = new ArrayList<>();

    /** True when more rows failed than the run is willing to list. */
    private boolean errorsTruncated;

    /**
     * Per-row leniencies: a value that could not be translated and was left blank, a
     * reference the run created, a column it could not use. Informational — a note never
     * means a row was refused.
     */
    private List<RowNote> notes = new ArrayList<>();

    /** True when more notes were raised than the run is willing to list. */
    private boolean notesTruncated;

    /**
     * A problem with the file or the mapping rather than with any row: a required field
     * that no column feeds, two fields pointed at one column, an unreadable file. When
     * this is set no rows were attempted and {@link #outcome} is {@link Outcome#FAILED}.
     */
    private String fatalError;

    /**
     * Why the run stopped before the end of the file, if it did — 'skip invalid rows'
     * was off and a row failed, or the file was longer than the row ceiling. A state,
     * not an error: rows written before the stop remain written.
     */
    private String stoppedReason;

    /** True when {@link #stoppedReason} is set. Convenience for the UI. */
    private boolean stoppedEarly;

    /**
     * Records the run created on the caller's behalf, by type:
     * {@code {"department": ["Finance", "IT"]}}. Empty on a dry run that would have
     * created them — see {@link #wouldCreateReferences}.
     */
    private Map<String, List<String>> createdReferences = new LinkedHashMap<>();

    /** Custom field definitions the run created from column headers. */
    private List<String> createdCustomFields = new ArrayList<>();

    /**
     * True on a dry run to say that {@link #createdReferences} names what a real run
     * <em>would</em> create, not what it did. Always false on a real run.
     */
    private boolean wouldCreateReferences;

    /** What a result screen should say happened. */
    public enum Outcome {
        /** Every row read was written, and nothing was read that was not written. */
        SUCCESS,
        /** Some rows were written and some were not. */
        PARTIAL,
        /** Rows were read and none of them were written. */
        FAILED,
        /** The file had no data rows at all. */
        NOTHING_TO_IMPORT
    }

    /**
     * Derives {@link #outcome} from the counts. Called once the run is over; keeping it
     * here rather than in the engine means a result rebuilt from a stored job row gets
     * the same verdict as the run that produced it.
     */
    public void settleOutcome() {
        if (fatalError != null && !fatalError.isBlank()) {
            outcome = Outcome.FAILED;
            return;
        }
        int written = imported + updated;
        if (totalRows == 0) {
            outcome = Outcome.NOTHING_TO_IMPORT;
        } else if (written == 0) {
            outcome = Outcome.FAILED;
        } else if (skipped > 0 || stoppedEarly) {
            outcome = Outcome.PARTIAL;
        } else {
            outcome = Outcome.SUCCESS;
        }
    }

    /**
     * A row that did not import, and why.
     *
     * <p>{@code column} is the header <em>as the user wrote it</em>, not the internal
     * field name: the point of the message is that someone can find the cell in their
     * own spreadsheet. {@code field} is the internal name, for the wizard to highlight
     * the right mapping row.</p>
     *
     * <p>Both are nullable — a whole-row problem has no one column — and the type stays
     * tolerant of unknown properties so results persisted by an older build still
     * deserialise.</p>
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RowError {
        private int row;
        private String message;
        private String field;
        private String column;

        public RowError() {
        }

        public RowError(int row, String message) {
            this.row = row;
            this.message = message;
        }

        public RowError(int row, String message, String field, String column) {
            this.row = row;
            this.message = message;
            this.field = field;
            this.column = column;
        }
    }

    /**
     * Something the importer did to a row rather than to the user: translated a value,
     * dropped one it could not translate, skipped a column it could not use.
     *
     * <p>A note is not an error and never reduces the imported count. It exists so that
     * "we accepted your file" does not quietly mean "we threw away your status column".</p>
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RowNote {
        private int row;
        private String message;
        private String field;
        private String column;
        /** The cell as the user wrote it, so they can find it. */
        private String value;

        public RowNote() {
        }

        public RowNote(int row, String message, String field, String column, String value) {
            this.row = row;
            this.message = message;
            this.field = field;
            this.column = column;
            this.value = value;
        }
    }
}
