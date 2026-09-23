package com.assetiq.imports;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns an uploaded .xlsx or .csv into a {@link ParsedSheet}.
 *
 * <p>Every cell becomes the string a human would see in the file, using POI's
 * {@link DataFormatter} so a date cell reads as its displayed date rather than an Excel
 * serial number. Type interpretation happens exactly once afterwards, in
 * {@link ImportRow}, so .xlsx and .csv cannot drift apart in what they accept.</p>
 */
@Component
public class SpreadsheetReader {

    /**
     * Hard ceiling on data rows read from one file. A file larger than this is read up
     * to the cap and flagged truncated rather than being allowed to exhaust the heap:
     * the row parser holds every cell of every row as a String.
     */
    public static final int MAX_ROWS = 20_000;

    /** Ceiling on columns, for the same reason. Nobody maps 500 columns by hand. */
    public static final int MAX_COLUMNS = 256;

    private final DataFormatter dataFormatter = new DataFormatter();

    public ParsedSheet read(String filename, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        return ImportUploadPolicy.isCsv(filename, bytes) ? readCsv(bytes) : readXlsx(bytes);
    }

    // ── .xlsx ─────────────────────────────────────────────────────────────────

    private ParsedSheet readXlsx(byte[] bytes) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("Workbook has no sheets");
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new IllegalArgumentException("The first sheet has no header row");
            }

            int width = Math.min(Math.max(headerRow.getLastCellNum(), 0), MAX_COLUMNS);
            List<String> headers = new ArrayList<>(width);
            for (int c = 0; c < width; c++) {
                headers.add(cellText(headerRow, c));
            }
            headers = trimTrailingBlanks(headers);
            if (headers.isEmpty()) {
                throw new IllegalArgumentException("The first sheet has no header row");
            }

            List<List<String>> rows = new ArrayList<>();
            boolean truncated = false;
            int firstDataRow = sheet.getFirstRowNum() + 1;
            for (int r = firstDataRow; r <= sheet.getLastRowNum(); r++) {
                if (rows.size() >= MAX_ROWS) { truncated = true; break; }
                Row row = sheet.getRow(r);
                List<String> values = new ArrayList<>(headers.size());
                for (int c = 0; c < headers.size(); c++) {
                    values.add(row == null ? null : cellText(row, c));
                }
                rows.add(values);
            }
            return new ParsedSheet(headers, stripTrailingBlankRows(rows), truncated);
        } catch (IOException | RuntimeException e) {
            if (e instanceof IllegalArgumentException illegal) throw illegal;
            throw new IllegalArgumentException("Failed to read the spreadsheet: " + e.getMessage(), e);
        }
    }

    private String cellText(Row row, int column) {
        Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        String value = dataFormatter.formatCellValue(cell);
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ── .csv ──────────────────────────────────────────────────────────────────

    private ParsedSheet readCsv(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1); // Excel writes a BOM; it is not part of the first header
        }
        char delimiter = detectDelimiter(text);
        List<List<String>> all = parseCsv(text, delimiter);
        if (all.isEmpty()) {
            throw new IllegalArgumentException("The file has no header row");
        }
        List<String> headers = trimTrailingBlanks(all.get(0));
        if (headers.isEmpty()) {
            throw new IllegalArgumentException("The file has no header row");
        }
        if (headers.size() > MAX_COLUMNS) {
            headers = headers.subList(0, MAX_COLUMNS);
        }

        List<List<String>> rows = new ArrayList<>();
        boolean truncated = false;
        for (int i = 1; i < all.size(); i++) {
            if (rows.size() >= MAX_ROWS) { truncated = true; break; }
            List<String> source = all.get(i);
            List<String> values = new ArrayList<>(headers.size());
            for (int c = 0; c < headers.size(); c++) {
                values.add(c < source.size() ? source.get(c) : null);
            }
            rows.add(values);
        }
        return new ParsedSheet(headers, stripTrailingBlankRows(rows), truncated);
    }

    /**
     * Pick the delimiter by counting candidates outside quotes on the header line.
     * Exports from European locales use {@code ;} and some tools emit tabs; guessing
     * wrong yields one giant column, which the wizard would then be unable to map.
     */
    private char detectDelimiter(String text) {
        int newline = text.indexOf('\n');
        String header = newline < 0 ? text : text.substring(0, newline);
        char best = ',';
        int bestCount = 0;
        for (char candidate : new char[]{',', ';', '\t', '|'}) {
            int count = 0;
            boolean inQuotes = false;
            for (int i = 0; i < header.length(); i++) {
                char ch = header.charAt(i);
                if (ch == '"') inQuotes = !inQuotes;
                else if (ch == candidate && !inQuotes) count++;
            }
            if (count > bestCount) { bestCount = count; best = candidate; }
        }
        return best;
    }

    /** A minimal RFC 4180 reader: quoted fields, doubled quotes, embedded newlines. */
    private List<List<String>> parseCsv(String text, char delimiter) {
        List<List<String>> rows = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean rowHasContent = false;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else inQuotes = false;
                } else {
                    field.append(ch);
                }
                continue;
            }
            if (ch == '"') {
                inQuotes = true;
                rowHasContent = true;
            } else if (ch == delimiter) {
                current.add(emptyToNull(field.toString()));
                field.setLength(0);
                rowHasContent = true;
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                current.add(emptyToNull(field.toString()));
                field.setLength(0);
                if (rowHasContent || current.stream().anyMatch(v -> v != null)) {
                    rows.add(current);
                }
                current = new ArrayList<>();
                rowHasContent = false;
                if (rows.size() > MAX_ROWS + 1) break;
            } else {
                field.append(ch);
                rowHasContent = true;
            }
        }
        if (field.length() > 0 || rowHasContent) {
            current.add(emptyToNull(field.toString()));
            rows.add(current);
        }
        return rows;
    }

    private static String emptyToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ── Shared tidy-up ────────────────────────────────────────────────────────

    private static List<String> trimTrailingBlanks(List<String> headers) {
        int end = headers.size();
        while (end > 0 && (headers.get(end - 1) == null || headers.get(end - 1).isBlank())) end--;
        return new ArrayList<>(headers.subList(0, end));
    }

    /** Drop the empty rows every spreadsheet accumulates at the bottom. */
    private static List<List<String>> stripTrailingBlankRows(List<List<String>> rows) {
        int end = rows.size();
        while (end > 0 && rows.get(end - 1).stream().allMatch(v -> v == null || v.isBlank())) end--;
        return new ArrayList<>(rows.subList(0, end));
    }
}
