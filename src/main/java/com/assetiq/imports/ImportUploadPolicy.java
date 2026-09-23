package com.assetiq.imports;

import com.assetiq.security.SpreadsheetUploadPolicy;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The gate every import upload passes through.
 *
 * <p>An import file is a file from a stranger, so the same three rules as the document
 * attachment surface apply: a size ceiling, an allow-list of two formats, and a check
 * that the <em>bytes</em> are what the extension claims. The extension alone is a
 * client-supplied string.</p>
 *
 * <p>.xlsx delegates to {@link SpreadsheetUploadPolicy}, which already refuses macros,
 * external links, embedded objects, zip-slip paths and zip bombs — one definition of
 * "is this workbook safe", shared with the legacy asset import.</p>
 *
 * <p>.csv is accepted only when the bytes decode as UTF-8 text and contain no NUL or
 * other control characters beyond tab/CR/LF, which is what a renamed binary would
 * carry. Note what this is not: no malware scanning happens here, exactly as documented
 * for attachments.</p>
 */
public final class ImportUploadPolicy {

    /** Same 10 MB ceiling the existing spreadsheet import enforces. */
    public static final long MAX_BYTES = SpreadsheetUploadPolicy.MAX_COMPRESSED_BYTES;

    public static final String XLSX_CONTENT_TYPE = SpreadsheetUploadPolicy.XLSX_CONTENT_TYPE;
    public static final String CSV_CONTENT_TYPE = "text/csv";

    private ImportUploadPolicy() {}

    /** Validates and returns the content type the file will be stored under. */
    public static String validate(String filename, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Import file exceeds the 10 MB limit");
        }
        String clean = SpreadsheetUploadPolicy.sanitiseFilename(filename);
        String lower = clean.toLowerCase(Locale.ROOT);

        if (lower.endsWith(".xlsx")) {
            SpreadsheetUploadPolicy.validate(clean, bytes);
            return XLSX_CONTENT_TYPE;
        }
        if (lower.endsWith(".csv")) {
            validateCsvBytes(bytes);
            return CSV_CONTENT_TYPE;
        }
        throw new IllegalArgumentException("Only .xlsx and .csv files can be imported");
    }

    /** True when this upload should be read as CSV rather than as a workbook. */
    public static boolean isCsv(String filename, byte[] bytes) {
        if (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            return true;
        }
        // An .xlsx is a zip; anything that is not is treated as text.
        return !(bytes != null && bytes.length >= 4
                && bytes[0] == 0x50 && bytes[1] == 0x4b && bytes[2] == 0x03 && bytes[3] == 0x04);
    }

    private static void validateCsvBytes(byte[] bytes) {
        // A zip, PDF or image renamed to .csv is rejected here rather than producing a
        // baffling parse result later.
        if (bytes.length >= 4 && bytes[0] == 0x50 && bytes[1] == 0x4b) {
            throw new IllegalArgumentException("File content is not CSV text — it looks like a zip or .xlsx package");
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException notText) {
            throw new IllegalArgumentException("File content is not valid UTF-8 CSV text");
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch < 0x20 && ch != '\t' && ch != '\n' && ch != '\r') {
                throw new IllegalArgumentException("File content is not CSV text — it contains binary data");
            }
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
    }
}
