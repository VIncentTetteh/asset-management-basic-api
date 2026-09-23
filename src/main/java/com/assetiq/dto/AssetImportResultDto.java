package com.assetiq.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * The outcome of an import run.
 *
 * <p>Named for assets for historical reasons; it is the result shape for every entity
 * type the generic import framework handles. {@code updated} is only ever non-zero when
 * the run was asked to update duplicates rather than skip them.</p>
 */
@Data
public class AssetImportResultDto {

    private int totalRows;
    private int imported;
    private int updated;
    private int skipped;
    private boolean dryRun;
    private List<RowError> errors = new ArrayList<>();

    /**
     * A row that did not import, and why.
     *
     * <p>{@code column} is the header <em>as the user wrote it</em>, not the internal
     * field name: the point of the message is that someone can find the cell in their
     * own spreadsheet. {@code field} is the internal name, for the wizard to highlight
     * the right mapping row.</p>
     *
     * <p>Both are nullable — a whole-row or whole-file problem has no one column — and
     * the type stays tolerant of unknown properties so results persisted by an older
     * build still deserialise.</p>
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
}
