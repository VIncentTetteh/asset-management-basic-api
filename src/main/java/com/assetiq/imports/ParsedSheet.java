package com.assetiq.imports;

import java.util.List;

/**
 * A spreadsheet read into memory as text: one header row and N data rows, every cell
 * already formatted to the string the user would see.
 *
 * <p>Reading to strings once, here, is what lets one engine serve .xlsx and .csv and
 * lets {@link ImportRow} own all type coercion. Row width is normalised to the header
 * width so a short row is missing cells, not out of bounds.</p>
 *
 * @param headers  the header row, in file order
 * @param rows     data rows, each the same length as {@code headers}
 * @param truncated true when the file had more rows than {@link SpreadsheetReader#MAX_ROWS}
 */
public record ParsedSheet(List<String> headers, List<List<String>> rows, boolean truncated) {

    public ParsedSheet {
        headers = List.copyOf(headers);
        rows = List.copyOf(rows);
    }

    public int columnCount() {
        return headers.size();
    }

    public int rowCount() {
        return rows.size();
    }

    /** Up to {@code limit} non-blank distinct values from one column, for the wizard. */
    public List<String> sampleValues(int columnIndex, int limit) {
        java.util.LinkedHashSet<String> samples = new java.util.LinkedHashSet<>();
        for (List<String> row : rows) {
            if (samples.size() >= limit) break;
            if (columnIndex >= row.size()) continue;
            String value = row.get(columnIndex);
            if (value != null && !value.isBlank()) samples.add(value.trim());
        }
        return List.copyOf(samples);
    }
}
