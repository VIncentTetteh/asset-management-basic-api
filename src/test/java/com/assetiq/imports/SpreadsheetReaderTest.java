package com.assetiq.imports;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** .xlsx and .csv must arrive at the engine looking identical. */
class SpreadsheetReaderTest {

    private final SpreadsheetReader reader = new SpreadsheetReader();

    @Test
    void readsCsvWithQuotedFieldsAndEmbeddedSeparators() {
        String csv = "Name,Notes,Qty\r\n\"Widget, large\",\"He said \"\"hi\"\"\",7\r\n";
        ParsedSheet sheet = reader.read("things.csv", csv.getBytes(StandardCharsets.UTF_8));

        assertThat(sheet.headers()).containsExactly("Name", "Notes", "Qty");
        assertThat(sheet.rows()).singleElement()
                .isEqualTo(java.util.Arrays.asList("Widget, large", "He said \"hi\"", "7"));
    }

    @Test
    void stripsTheByteOrderMarkExcelWrites() {
        String csv = "﻿Name,Qty\n" + "Widget,1\n";
        ParsedSheet sheet = reader.read("things.csv", csv.getBytes(StandardCharsets.UTF_8));

        // Without this the first header is "﻿Name" and never matches anything.
        assertThat(sheet.headers()).containsExactly("Name", "Qty");
    }

    @Test
    void detectsSemicolonDelimitedExports() {
        // European locales export with semicolons. Guessing wrong yields one giant
        // column, which cannot be mapped at all.
        String csv = "Name;Qty;Notes\nWidget;3;fine\n";
        ParsedSheet sheet = reader.read("things.csv", csv.getBytes(StandardCharsets.UTF_8));

        assertThat(sheet.headers()).containsExactly("Name", "Qty", "Notes");
        assertThat(sheet.rows().get(0)).containsExactly("Widget", "3", "fine");
    }

    @Test
    void shortRowsBecomeMissingCellsNotAnOutOfBounds() {
        String csv = "Name,Qty,Notes\nWidget\n";
        ParsedSheet sheet = reader.read("things.csv", csv.getBytes(StandardCharsets.UTF_8));

        assertThat(sheet.rows().get(0)).hasSize(3);
        assertThat(sheet.rows().get(0).get(2)).isNull();
    }

    @Test
    void trailingBlankRowsAreDropped() {
        String csv = "Name,Qty\nWidget,1\n,\n,\n";
        ParsedSheet sheet = reader.read("things.csv", csv.getBytes(StandardCharsets.UTF_8));

        assertThat(sheet.rowCount()).isEqualTo(1);
    }

    @Test
    void xlsxDateCellsComeBackAsTheDisplayedDateNotAnExcelSerialNumber() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("When");
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(Date.from(LocalDate.of(2024, 3, 12)
                    .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
            row.getCell(0).setCellStyle(dateStyle);
            workbook.write(out);
            bytes = out.toByteArray();
        }

        ParsedSheet sheet = reader.read("dates.xlsx", bytes);
        assertThat(sheet.rows().get(0).get(0)).isEqualTo("2024-03-12");
    }

    @Test
    void sampleValuesSkipBlanksAndDeDuplicate() {
        String csv = "Name\nA\n\nA\nB\n";
        ParsedSheet sheet = reader.read("things.csv", csv.getBytes(StandardCharsets.UTF_8));

        assertThat(sheet.sampleValues(0, 3)).containsExactly("A", "B");
    }

    @Test
    void aFileWithNoHeaderRowIsRejectedPlainly() {
        assertThatThrownBy(() -> reader.read("empty.csv", "\n\n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("header row");
    }
}
