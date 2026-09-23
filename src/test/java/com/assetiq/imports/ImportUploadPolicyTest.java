package com.assetiq.imports;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Import files come from strangers. The extension is a string the client typed, so the
 * bytes decide.
 */
class ImportUploadPolicyTest {

    @Test
    void acceptsARealXlsx() throws Exception {
        assertThat(ImportUploadPolicy.validate("things.xlsx", xlsxBytes()))
                .isEqualTo(ImportUploadPolicy.XLSX_CONTENT_TYPE);
    }

    @Test
    void acceptsRealCsvText() {
        assertThat(ImportUploadPolicy.validate("things.csv", "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(ImportUploadPolicy.CSV_CONTENT_TYPE);
    }

    @Test
    void refusesEverythingOutsideTheTwoAllowedFormats() {
        assertThatThrownBy(() -> ImportUploadPolicy.validate("things.pdf", "%PDF-1.4".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".xlsx and .csv");
    }

    @Test
    void refusesAZipRenamedToCsv() throws Exception {
        // The classic: upload an archive, call it text, hope the parser is forgiving.
        assertThatThrownBy(() -> ImportUploadPolicy.validate("things.csv", xlsxBytes()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not CSV text");
    }

    @Test
    void refusesBinaryContentRenamedToCsv() {
        byte[] binary = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
        assertThatThrownBy(() -> ImportUploadPolicy.validate("image.csv", binary))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesTextRenamedToXlsx() {
        assertThatThrownBy(() -> ImportUploadPolicy.validate("fake.xlsx", "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a valid .xlsx package");
    }

    @Test
    void enforcesTheSameSizeCapAsTheDocumentSurface() {
        byte[] tooBig = new byte[(int) ImportUploadPolicy.MAX_BYTES + 1];
        assertThatThrownBy(() -> ImportUploadPolicy.validate("big.csv", tooBig))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 MB");
    }

    @Test
    void anXlsxIsReadAsAWorkbookAndTextIsReadAsCsv() throws Exception {
        assertThat(ImportUploadPolicy.isCsv("things.xlsx", xlsxBytes())).isFalse();
        assertThat(ImportUploadPolicy.isCsv("things.csv", "a,b".getBytes(StandardCharsets.UTF_8))).isTrue();
    }

    private static byte[] xlsxBytes() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet().createRow(0).createCell(0).setCellValue("Name");
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
