package com.assetiq.security;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpreadsheetUploadPolicyTest {

    @Test
    void acceptsMinimalMacroFreeXlsxPackage() throws Exception {
        byte[] workbook = zip(Map.of(
                "[Content_Types].xml", "types",
                "xl/workbook.xml", "workbook"));

        assertThatCode(() -> SpreadsheetUploadPolicy.validate("assets.xlsx", workbook))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsSpoofedExtensionAndActiveContent() throws Exception {
        assertThatThrownBy(() -> SpreadsheetUploadPolicy.validate("assets.xlsx", "not-a-zip".getBytes()))
                .hasMessageContaining("not a valid .xlsx");

        byte[] macro = zip(Map.of(
                "[Content_Types].xml", "types",
                "xl/workbook.xml", "workbook",
                "xl/vbaProject.bin", "macro"));
        assertThatThrownBy(() -> SpreadsheetUploadPolicy.validate("assets.xlsx", macro))
                .hasMessageContaining("Macros");
    }

    @Test
    void stripsClientPathsFromStoredFilename() {
        org.assertj.core.api.Assertions.assertThat(
                SpreadsheetUploadPolicy.sanitiseFilename("C:\\fakepath\\asset import.xlsx"))
                .isEqualTo("asset_import.xlsx");
    }

    private byte[] zip(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
