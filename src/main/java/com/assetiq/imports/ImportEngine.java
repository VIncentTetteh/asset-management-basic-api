package com.assetiq.imports;

import com.assetiq.dto.AssetImportResultDto;
import com.assetiq.dto.AssetImportResultDto.RowError;
import com.assetiq.models.Organisation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runs a parsed sheet through an {@link ImportEntityHandler}, one row at a time.
 *
 * <h2>Failure semantics</h2>
 * <p>An import is <b>best-effort with a report</b>, not all-or-nothing. Each row is
 * applied in its own {@code REQUIRES_NEW} transaction, so a row that violates a
 * constraint rolls back alone and rows 1..n-1 stay committed. The result says exactly
 * how many were written, how many were updated, how many were skipped and what was
 * wrong with each failure.</p>
 *
 * <p>That is a deliberate choice over all-or-nothing. Someone migrating 3000 rows from
 * another platform will have a handful of bad ones; refusing the whole file until the
 * sheet is perfect makes the migration a loop of full re-uploads. The preview step
 * exists so nobody is surprised by which rows those are, and the UI shows the real
 * counts rather than a green tick.</p>
 *
 * <h2>Row errors</h2>
 * <p>Every error names the spreadsheet row number, the column header <em>as the user
 * wrote it</em>, and what was wrong. "Validation failed" helps nobody.</p>
 */
@Component
public class ImportEngine {

    private static final Logger log = LoggerFactory.getLogger(ImportEngine.class);

    /** Ceiling on errors carried back. Past this the file is wrong, not the rows. */
    public static final int MAX_REPORTED_ERRORS = 500;

    /** Longest custom-field header the asset path will accept; matches the column limit. */
    private static final int MAX_CUSTOM_FIELD_HEADER_LENGTH = 100;

    private final TransactionTemplate rowTransactionTemplate;

    public ImportEngine(PlatformTransactionManager transactionManager) {
        this.rowTransactionTemplate = new TransactionTemplate(transactionManager);
        this.rowTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * @param sheet    the uploaded file, already parsed
     * @param mapping  field name → column index; a null or absent value means unmapped
     * @param handler  the entity handler for this type
     * @param options  duplicate handling, reference creation, dry run
     * @param org      the tenant; every row is written against this organisation only
     * @param rowLimit stop after this many data rows (preview caps it; commit does not)
     */
    public AssetImportResultDto run(ParsedSheet sheet,
                                    Map<String, Integer> mapping,
                                    ImportEntityHandler handler,
                                    ImportOptions options,
                                    Organisation org,
                                    int rowLimit) {
        AssetImportResultDto result = new AssetImportResultDto();
        result.setDryRun(options.dryRun());

        Map<String, Integer> resolved = validateMapping(sheet, mapping, handler, result);
        if (resolved == null) {
            return result;
        }

        Map<String, String> headerByField = new LinkedHashMap<>();
        resolved.forEach((field, column) -> headerByField.put(field, headerText(sheet, column, field)));

        List<UnmappedColumn> unmappedColumns;
        try {
            unmappedColumns = resolveUnmappedColumns(sheet, resolved, handler, options);
        } catch (IllegalArgumentException badHeader) {
            result.getErrors().add(new RowError(1, badHeader.getMessage()));
            return result;
        }

        ImportEntityHandler.ImportRunner runner = handler.runner(org, options);

        int limit = rowLimit <= 0 ? sheet.rowCount() : Math.min(rowLimit, sheet.rowCount());
        for (int i = 0; i < limit; i++) {
            List<String> values = sheet.rows().get(i);
            int humanRow = i + 2; // header is row 1

            Map<String, String> fieldValues = new LinkedHashMap<>();
            resolved.forEach((field, column) -> fieldValues.put(field, valueAt(values, column)));
            Map<String, String> unmapped = new LinkedHashMap<>();
            for (UnmappedColumn column : unmappedColumns) {
                String value = valueAt(values, column.index());
                if (value != null && !value.isBlank()) unmapped.put(column.header(), value);
            }

            ImportRow row = new ImportRow(humanRow, fieldValues, headerByField, unmapped);
            if (row.isBlank()) {
                continue; // a blank line in the middle of a sheet is not a failed row
            }
            result.setTotalRows(result.getTotalRows() + 1);
            int failuresBefore = result.getSkipped();
            applyRow(runner, row, result);

            boolean rowFailed = result.getSkipped() > failuresBefore
                    && !result.getErrors().isEmpty()
                    && result.getErrors().get(result.getErrors().size() - 1).getRow() == humanRow;
            if (rowFailed && !options.skipInvalidRows()) {
                // The caller asked to stop at the first bad row. Rows already written
                // stay written -- see the class comment on why an import is best-effort
                // rather than all-or-nothing -- so say so plainly.
                result.getErrors().add(new RowError(0,
                        "Stopped at row " + humanRow + " because 'skip invalid rows' is off."
                                + (options.dryRun() ? "" : " Rows before it were imported and remain imported.")));
                return result;
            }
        }

        if (sheet.truncated()) {
            result.getErrors().add(new RowError(0,
                    "Only the first " + SpreadsheetReader.MAX_ROWS
                            + " rows were read. Split the file and import the rest separately."));
        }
        return result;
    }

    private void applyRow(ImportEntityHandler.ImportRunner runner, ImportRow row, AssetImportResultDto result) {
        try {
            ImportEntityHandler.RowOutcome outcome =
                    rowTransactionTemplate.execute(status -> runner.apply(row));
            switch (outcome == null ? ImportEntityHandler.RowOutcome.Kind.SKIPPED : outcome.kind()) {
                case CREATED -> result.setImported(result.getImported() + 1);
                case UPDATED -> result.setUpdated(result.getUpdated() + 1);
                case SKIPPED -> {
                    result.setSkipped(result.getSkipped() + 1);
                    if (outcome != null && outcome.message() != null) {
                        addError(result, new RowError(row.rowNumber(), outcome.message()));
                    }
                }
            }
        } catch (FieldValidationException e) {
            result.setSkipped(result.getSkipped() + 1);
            String column = row.headerFor(e.getFieldName());
            addError(result, new RowError(row.rowNumber(),
                    "Column '" + column + "' " + e.getMessage(), e.getFieldName(), column));
        } catch (IllegalArgumentException | IllegalStateException | AccessDeniedException e) {
            result.setSkipped(result.getSkipped() + 1);
            addError(result, new RowError(row.rowNumber(), e.getMessage()));
        } catch (Exception e) {
            log.warn("Import: unexpected error at row {}", row.rowNumber(), e);
            result.setSkipped(result.getSkipped() + 1);
            addError(result, new RowError(row.rowNumber(), "Unexpected error: " + e.getMessage()));
        }
    }

    private void addError(AssetImportResultDto result, RowError error) {
        if (result.getErrors().size() < MAX_REPORTED_ERRORS) {
            result.getErrors().add(error);
        } else if (result.getErrors().size() == MAX_REPORTED_ERRORS) {
            result.getErrors().add(new RowError(0,
                    "More than " + MAX_REPORTED_ERRORS + " rows failed; further errors are not listed."));
        }
    }

    /**
     * Rejects a mapping before any row is touched: unknown fields, out-of-range column
     * indices, two fields pointed at one column, and missing required fields.
     */
    private Map<String, Integer> validateMapping(ParsedSheet sheet,
                                                 Map<String, Integer> mapping,
                                                 ImportEntityHandler handler,
                                                 AssetImportResultDto result) {
        Map<String, ImportFieldDescriptor> byName = new LinkedHashMap<>();
        handler.fields().forEach(f -> byName.put(f.name(), f));

        Map<String, Integer> resolved = new LinkedHashMap<>();
        Map<Integer, String> claimedBy = new LinkedHashMap<>();

        if (mapping != null) {
            for (Map.Entry<String, Integer> entry : mapping.entrySet()) {
                String field = entry.getKey();
                Integer column = entry.getValue();
                if (column == null) continue;
                if (!byName.containsKey(field)) {
                    result.getErrors().add(new RowError(0,
                            "'" + field + "' is not a field of " + handler.entityType().label().toLowerCase(Locale.ROOT)));
                    return null;
                }
                if (column < 0 || column >= sheet.columnCount()) {
                    result.getErrors().add(new RowError(0,
                            "Field '" + field + "' is mapped to column " + column
                                    + ", but the file has " + sheet.columnCount() + " columns"));
                    return null;
                }
                String previous = claimedBy.putIfAbsent(column, field);
                if (previous != null) {
                    result.getErrors().add(new RowError(0,
                            "Column '" + headerText(sheet, column, "#" + column)
                                    + "' is mapped to both '" + previous + "' and '" + field + "'"));
                    return null;
                }
                resolved.put(field, column);
            }
        }

        List<String> missing = byName.values().stream()
                .filter(ImportFieldDescriptor::required)
                .map(ImportFieldDescriptor::name)
                .filter(name -> !resolved.containsKey(name))
                .toList();
        if (!missing.isEmpty()) {
            result.getErrors().add(new RowError(0,
                    "These required fields are not mapped to a column: " + String.join(", ", missing)));
            return null;
        }
        return resolved;
    }

    /**
     * Columns the mapping did not claim, but only when the run asked for them.
     *
     * <p>On the mapping-driven path {@link ImportOptions#captureUnmappedColumns()} is
     * false and this returns nothing, so an unmapped column is simply not read — no
     * error, no custom field. That is the whole point of the feature: a sheet exported
     * from another platform carries columns AssetIQ has no field for, and the answer to
     * them is to ignore them, not to send the customer away to edit their spreadsheet.
     *
     * <p>Only the legacy positional asset import turns this on, where extra columns
     * past the fixed layout have always become custom fields behind a feature flag.</p>
     */
    private List<UnmappedColumn> resolveUnmappedColumns(ParsedSheet sheet,
                                                        Map<String, Integer> resolved,
                                                        ImportEntityHandler handler,
                                                        ImportOptions options) {
        if (!options.captureUnmappedColumns() || !handler.unmappedColumnsBecomeCustomFields()) {
            return List.of();
        }
        java.util.Set<Integer> mapped = new java.util.HashSet<>(resolved.values());
        List<UnmappedColumn> columns = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < sheet.columnCount(); i++) {
            if (mapped.contains(i)) continue;
            String header = sheet.headers().get(i);
            if (header == null || header.isBlank()) continue;
            if (header.length() > MAX_CUSTOM_FIELD_HEADER_LENGTH) {
                throw new IllegalArgumentException(
                        "Custom field header '" + header + "' exceeds the "
                                + MAX_CUSTOM_FIELD_HEADER_LENGTH + " character limit");
            }
            if (!seen.add(header.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(
                        "Duplicate custom field header '" + header + "' found in the import file");
            }
            columns.add(new UnmappedColumn(i, header));
        }
        return columns;
    }

    private static String headerText(ParsedSheet sheet, int column, String fallback) {
        if (column < 0 || column >= sheet.headers().size()) return fallback;
        String header = sheet.headers().get(column);
        return header == null || header.isBlank() ? fallback : header;
    }

    private static String valueAt(List<String> values, int column) {
        return column >= 0 && column < values.size() ? values.get(column) : null;
    }

    private record UnmappedColumn(int index, String header) {}
}
