package com.assetiq.dto;

import java.util.List;

/**
 * A dry run over the first N rows: what would have happened, with nothing written.
 *
 * @param totals    valid / invalid / total across the rows that were checked
 * @param errors    per-row errors, each naming the row, the user's own column header
 *                  and what was wrong
 * @param rowsChecked how many data rows the preview actually looked at
 * @param totalRowsInFile how many the file has, so the UI can say "first 100 of 3000"
 */
public record ImportPreviewDto(
        Totals totals,
        List<AssetImportResultDto.RowError> errors,
        int rowsChecked,
        int totalRowsInFile
) {
    public record Totals(int valid, int invalid, int total) {}
}
