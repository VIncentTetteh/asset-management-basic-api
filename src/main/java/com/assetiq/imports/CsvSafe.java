package com.assetiq.imports;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Writes CSV that a spreadsheet application will open as data, not as a program.
 *
 * <p>A CSV cell whose text starts {@code =}, {@code +}, {@code -} or {@code @} is
 * evaluated as a formula by Excel, LibreOffice and Google Sheets when the file is
 * opened — {@code =HYPERLINK(...)}, {@code =cmd|'/c calc'!A1} and friends. The file
 * does not have to come from an attacker for this to matter: a value we round-trip
 * out of the database is enough. Every field written here is escaped, and the tab and
 * carriage-return leaders that bypass a naive check are stripped first.</p>
 *
 * <p>The same rule is applied to .xlsx cells generated for templates: POI writes the
 * text as a string cell, but the prefix is still what a user gets if they save the
 * sheet back out as CSV.</p>
 */
public final class CsvSafe {

    private static final String FORMULA_LEADERS = "=+-@";

    private CsvSafe() {}

    /**
     * Neutralises a value that a spreadsheet would treat as a formula, by prefixing a
     * single quote — the conventional "this is text" marker, which spreadsheet
     * applications strip on display.
     */
    public static String neutralise(String value) {
        if (value == null || value.isEmpty()) return value;
        // Leading tab/CR/LF are stripped by the parser before the formula check, so a
        // value starting "\t=" would otherwise slip through.
        int start = 0;
        while (start < value.length()
                && (value.charAt(start) == '\t' || value.charAt(start) == '\r' || value.charAt(start) == '\n')) {
            start++;
        }
        if (start >= value.length()) return value;
        char first = value.charAt(start);
        if (FORMULA_LEADERS.indexOf(first) >= 0) {
            return "'" + value.substring(start);
        }
        return value;
    }

    /** One CSV record, quoted where required and formula-neutralised throughout. */
    public static String line(List<String> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(quote(neutralise(fields.get(i))));
        }
        sb.append("\r\n");
        return sb.toString();
    }

    private static String quote(String value) {
        String v = value == null ? "" : value;
        if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }

    /** A whole CSV document, UTF-8, with the BOM Excel needs to read UTF-8 correctly. */
    public static byte[] document(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder("﻿");
        rows.forEach(row -> sb.append(line(row)));
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
