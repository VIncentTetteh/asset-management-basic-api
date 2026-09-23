package com.assetiq.dto;

import java.util.List;
import java.util.Map;

/**
 * A dry run over the first N rows: what would have happened, with nothing written.
 *
 * <p>The contract this type has to keep is that it agrees with the commit. Preview runs
 * the same engine, the same handler, the same value mappings and the same
 * reference-creation decisions as the real run — the only differences are the row cap
 * and that nothing is persisted. A preview that says every row passes and is then
 * followed by a failed import is the specific bug this shape exists to make
 * impossible.</p>
 *
 * @param totals          valid / invalid / total across the rows that were checked
 * @param errors          per-row errors, each naming the row, the user's own column
 *                        header and what was wrong. One entry per failed row, never a
 *                        synthetic row 0
 * @param notes           per-row leniencies: a value left blank because nothing
 *                        recognised it, a reference that could not be created. Not
 *                        failures
 * @param rowsChecked     how many data rows the preview actually looked at
 * @param totalRowsInFile how many the file has, so the UI can say "first 100 of 3000"
 * @param outcome         SUCCESS, PARTIAL, FAILED or NOTHING_TO_IMPORT — the verdict the
 *                        commit would reach on the rows checked
 * @param fatalError      a problem with the file or mapping rather than any row; when
 *                        set, no rows were checked and the commit would fail the same way
 * @param wouldCreate     records the commit would create on the caller's behalf, by
 *                        type: {@code {"department": ["Finance", "IT"]}}
 * @param wouldCreateCustomFields custom field definitions the commit would create
 */
public record ImportPreviewDto(
        Totals totals,
        List<AssetImportResultDto.RowError> errors,
        List<AssetImportResultDto.RowNote> notes,
        int rowsChecked,
        int totalRowsInFile,
        AssetImportResultDto.Outcome outcome,
        String fatalError,
        Map<String, List<String>> wouldCreate,
        List<String> wouldCreateCustomFields
) {
    public record Totals(int valid, int invalid, int total) {}
}
