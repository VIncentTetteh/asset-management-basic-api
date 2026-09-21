package com.assetiq.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Strict allowlist validation for the enabled asset-import upload surface. */
public final class SpreadsheetUploadPolicy {

    public static final long MAX_COMPRESSED_BYTES = 10L * 1024 * 1024;
    private static final long MAX_UNCOMPRESSED_BYTES = 50L * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 2_000;
    public static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final Set<String> REQUIRED_ENTRIES = Set.of("[content_types].xml", "xl/workbook.xml");

    private SpreadsheetUploadPolicy() {}

    public static void validate(String filename, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        if (bytes.length > MAX_COMPRESSED_BYTES) {
            throw new IllegalArgumentException("Spreadsheet exceeds the 10 MB import limit");
        }
        String clean = sanitiseFilename(filename);
        if (!clean.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("Only macro-free .xlsx files are supported");
        }
        if (bytes.length < 4 || bytes[0] != 0x50 || bytes[1] != 0x4b
                || bytes[2] != 0x03 || bytes[3] != 0x04) {
            throw new IllegalArgumentException("File content is not a valid .xlsx package");
        }

        java.util.HashSet<String> entries = new java.util.HashSet<>();
        long expanded = 0;
        int count = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                count++;
                if (count > MAX_ZIP_ENTRIES) throw new IllegalArgumentException("Spreadsheet package has too many entries");
                String name = entry.getName().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (name.startsWith("/") || name.contains("../")) {
                    throw new IllegalArgumentException("Spreadsheet package contains an unsafe path");
                }
                if (name.endsWith("vbaproject.bin") || name.startsWith("xl/externallinks/")
                        || name.startsWith("xl/embeddings/") || name.startsWith("xl/oleobjects/")) {
                    throw new IllegalArgumentException("Macros, external links, and embedded objects are not allowed");
                }
                entries.add(name);
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read;
                    if (expanded > MAX_UNCOMPRESSED_BYTES) {
                        throw new IllegalArgumentException("Spreadsheet expands beyond the 50 MB safety limit");
                    }
                }
            }
        } catch (IOException invalidZip) {
            throw new IllegalArgumentException("File content is not a readable .xlsx package", invalidZip);
        }
        if (!entries.containsAll(REQUIRED_ENTRIES)) {
            throw new IllegalArgumentException("Spreadsheet package is missing required workbook files");
        }
    }

    public static String sanitiseFilename(String filename) {
        String value = filename == null ? "" : filename.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) value = value.substring(slash + 1);
        value = value.replaceAll("[\\p{Cntrl}]", "").replaceAll("[^A-Za-z0-9._-]", "_");
        if (value.isBlank() || ".".equals(value) || "..".equals(value)) {
            throw new IllegalArgumentException("Uploaded filename is invalid");
        }
        return value;
    }
}
