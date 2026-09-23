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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
 * <h2>Counting</h2>
 * <p>{@code totalRows == imported + updated + skipped} and
 * {@code errors.size() == failed} both hold on every result — see
 * {@link AssetImportResultDto}. That is why nothing in here ever appends a row-0 entry
 * to the error list: a mapping problem is a {@code fatalError}, an early stop is a
 * {@code stoppedReason}, and neither is a row that failed.</p>
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
    public static final int MAX_CUSTOM_FIELD_HEADER_LENGTH = 100;

    private final TransactionTemplate rowTransactionTemplate;

    public ImportEngine(PlatformTransactionManager transactionManager) {
        this.rowTransactionTemplate = new TransactionTemplate(transactionManager);
        this.rowTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * @param sheet    the uploaded file, already parsed
     * @param mapping  field name → column index; a null or absent value means unmapped
     * @param handler  the entity handler for this type
     * @param options  duplicate handling, reference creation, value mappings, dry run
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
        result.setWouldCreateReferences(options.dryRun() && options.createMissingReferences());

        Map<String, Integer> resolved = validateMapping(sheet, mapping, handler, result);
        if (resolved == null) {
            result.settleOutcome();
            return result;
        }

        Map<String, String> headerByField = new LinkedHashMap<>();
        resolved.forEach((field, column) -> headerByField.put(field, headerText(sheet, column, field)));

        List<UnmappedColumn> customFieldColumns;
        try {
            customFieldColumns = resolveCustomFieldColumns(sheet, resolved, handler, options);
        } catch (IllegalArgumentException badHeader) {
            result.setFatalError(badHeader.getMessage());
            result.settleOutcome();
            return result;
        }

        ImportRunReport report = new ImportRunReport();
        report.useHeaders(headerByField);
        ImportValueMappings valueMappings = options.valueMappingIndex();
        Set<String> requiredFields = handler.fields().stream()
                .filter(ImportFieldDescriptor::required)
                .map(ImportFieldDescriptor::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        ImportEntityHandler.ImportRunner runner = handler.runner(org, options, report);

        int limit = rowLimit <= 0 ? sheet.rowCount() : Math.min(rowLimit, sheet.rowCount());
        for (int i = 0; i < limit; i++) {
            List<String> values = sheet.rows().get(i);
            int humanRow = i + 2; // header is row 1

            Map<String, String> fieldValues = new LinkedHashMap<>();
            resolved.forEach((field, column) -> fieldValues.put(field, valueAt(values, column)));
            Map<String, String> unmapped = new LinkedHashMap<>();
            for (UnmappedColumn column : customFieldColumns) {
                String value = valueAt(values, column.index());
                if (value != null && !value.isBlank()) unmapped.put(column.header(), value);
            }

            report.beginRow(humanRow);
            ImportRow row = new ImportRow(humanRow, fieldValues, headerByField, unmapped,
                    valueMappings, requiredFields, report);
            if (row.isBlank()) {
                report.discardRow();
                continue; // a blank line in the middle of a sheet is not a failed row
            }
            result.setTotalRows(result.getTotalRows() + 1);
            boolean rowFailed = applyRow(runner, row, result, report);

            if (rowFailed && !options.skipInvalidRows()) {
                // The caller asked to stop at the first bad row. Rows already written
                // stay written -- see the class comment on why an import is best-effort
                // rather than all-or-nothing -- so say so plainly, as a state of the run
                // and not as one more failed row.
                stop(result, "Stopped at row " + humanRow + " because 'skip invalid rows' is off."
                        + (options.dryRun() ? "" : " Rows before it were imported and remain imported."));
                return finish(result, report);
            }
        }

        if (sheet.truncated()) {
            stop(result, "Only the first " + SpreadsheetReader.MAX_ROWS
                    + " rows were read. Split the file and import the rest separately.");
        }
        return finish(result, report);
    }

    /**
     * Folds the run's notes and creations into the result and settles the verdict.
     * Every exit from {@link #run} goes through here, so no path can return a result
     * whose outcome was never computed.
     */
    private AssetImportResultDto finish(AssetImportResultDto result, ImportRunReport report) {
        result.getNotes().addAll(report.notes());
        result.setNotesTruncated(report.notesTruncated());
        result.setCreatedReferences(report.createdReferences());
        result.setCreatedCustomFields(report.createdCustomFields());
        result.setSkipped(result.getTotalRows() - result.getImported() - result.getUpdated());
        result.setDuplicatesSkipped(Math.max(0, result.getSkipped() - result.getFailed()));
        result.settleOutcome();
        return result;
    }

    private void stop(AssetImportResultDto result, String reason) {
        result.setStoppedEarly(true);
        result.setStoppedReason(reason);
    }

    /**
     * Applies one row and keeps or drops the notes it raised.
     *
     * <p>Notes survive only on a row that landed. A row that failed already carries an
     * error explaining itself, and pairing that with "by the way, we ignored your status
     * column" describes work on a record that was never written.</p>
     *
     * @return true when the row failed, so the caller can honour 'stop at first bad row'
     */
    private boolean applyRow(ImportEntityHandler.ImportRunner runner,
                             ImportRow row,
                             AssetImportResultDto result,
                             ImportRunReport report) {
        boolean failed = true;
        try {
            ImportEntityHandler.RowOutcome outcome =
                    rowTransactionTemplate.execute(status -> runner.apply(row));
            switch (outcome == null ? ImportEntityHandler.RowOutcome.Kind.SKIPPED : outcome.kind()) {
                case CREATED -> result.setImported(result.getImported() + 1);
                case UPDATED -> result.setUpdated(result.getUpdated() + 1);
                case SKIPPED -> {
                    if (outcome != null && outcome.message() != null) {
                        return failed = fail(result, new RowError(row.rowNumber(), outcome.message()));
                    }
                    // A deliberate duplicate skip. Not a failure, so no error, and the
                    // counts keep the error list the same length as the failure count.
                }
            }
            return failed = false;
        } catch (FieldValidationException e) {
            String column = row.headerFor(e.getFieldName());
            return failed = fail(result, new RowError(row.rowNumber(),
                    "Column '" + column + "' " + e.getMessage(), e.getFieldName(), column));
        } catch (IllegalArgumentException | IllegalStateException | AccessDeniedException e) {
            return failed = fail(result, new RowError(row.rowNumber(), e.getMessage()));
        } catch (Exception e) {
            log.warn("Import: unexpected error at row {}", row.rowNumber(), e);
            return failed = fail(result, new RowError(row.rowNumber(), "Unexpected error: " + e.getMessage()));
        } finally {
            if (failed) report.discardRow(); else report.commitRow();
        }
    }

    private boolean fail(AssetImportResultDto result, RowError error) {
        result.setFailed(result.getFailed() + 1);
        if (result.getErrors().size() < MAX_REPORTED_ERRORS) {
            result.getErrors().add(error);
        } else {
            result.setErrorsTruncated(true);
        }
        return true;
    }

    /**
     * Rejects a mapping before any row is touched: unknown fields, out-of-range column
     * indices, two fields pointed at one column, and missing required fields. These are
     * problems with the request, not with a row, so they land in
     * {@link AssetImportResultDto#getFatalError()} rather than in the error list.
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
                    result.setFatalError("'" + field + "' is not a field of "
                            + handler.entityType().label().toLowerCase(Locale.ROOT));
                    return null;
                }
                if (column < 0 || column >= sheet.columnCount()) {
                    result.setFatalError("Field '" + field + "' is mapped to column " + column
                            + ", but the file has " + sheet.columnCount() + " columns");
                    return null;
                }
                String previous = claimedBy.putIfAbsent(column, field);
                if (previous != null) {
                    result.setFatalError("Column '" + headerText(sheet, column, "#" + column)
                            + "' is mapped to both '" + previous + "' and '" + field + "'");
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
            result.setFatalError(
                    "These required fields are not mapped to a column: " + String.join(", ", missing));
            return null;
        }
        return resolved;
    }

    /**
     * The columns whose values become custom fields on each record.
     *
     * <p>Two ways in, and they answer different questions. The wizard names the columns
     * the user chose to keep, in {@link ImportOptions#customFieldColumns()}: a column
     * that is neither mapped nor named there was set to "ignore" and is not read at all,
     * which is the whole premise — a sheet from another platform carries columns AssetIQ
     * has no field for, and the user decides per column whether that is worth keeping.
     * The legacy positional asset import instead sets
     * {@link ImportOptions#captureUnmappedColumns()} and takes everything past the fixed
     * layout, as it always has.</p>
     */
    private List<UnmappedColumn> resolveCustomFieldColumns(ParsedSheet sheet,
                                                           Map<String, Integer> resolved,
                                                           ImportEntityHandler handler,
                                                           ImportOptions options) {
        boolean takeEverything = options.captureUnmappedColumns();
        Set<Integer> chosen = options.customFieldColumns();
        if (!handler.unmappedColumnsBecomeCustomFields() || (!takeEverything && chosen.isEmpty())) {
            return List.of();
        }
        if (chosen.size() > ImportOptions.MAX_CUSTOM_FIELD_COLUMNS) {
            throw new IllegalArgumentException(
                    "This import would create " + chosen.size() + " custom fields, past the limit of "
                            + ImportOptions.MAX_CUSTOM_FIELD_COLUMNS
                            + ". Keep the columns that matter and set the rest to ignore.");
        }

        Set<Integer> mapped = new HashSet<>(resolved.values());
        List<UnmappedColumn> columns = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < sheet.columnCount(); i++) {
            if (mapped.contains(i)) continue;
            if (!takeEverything && !chosen.contains(i)) continue;
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
