package com.assetiq.imports;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the downloadable template for an entity type, entirely from its descriptors.
 *
 * <p>The template cannot drift from what the importer accepts, because there is no
 * second list to drift from: headers are {@link ImportFieldDescriptor#label()}, the
 * example row is {@link ImportFieldDescriptor#example()}, and the documentation sheet
 * is the rest of the record. Change a descriptor and the template changes with it.</p>
 */
@Component
public class ImportTemplateGenerator {

    private static final String DATA_SHEET = "Data";
    private static final String DOCS_SHEET = "Column guide";

    /** The .xlsx: a header row, one example row, and a sheet documenting every column. */
    public byte[] xlsx(ImportEntityType type, List<ImportFieldDescriptor> fields) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle requiredHeaderStyle = requiredHeaderStyle(workbook);

            Sheet data = workbook.createSheet(DATA_SHEET);
            Row headerRow = data.createRow(0);
            Row exampleRow = data.createRow(1);
            for (int i = 0; i < fields.size(); i++) {
                ImportFieldDescriptor field = fields.get(i);
                Cell header = headerRow.createCell(i);
                header.setCellValue(field.label());
                header.setCellStyle(field.required() ? requiredHeaderStyle : headerStyle);

                Cell example = exampleRow.createCell(i);
                // Neutralised for the same reason as CSV: a template saved back out as
                // CSV, or pasted, must not carry a formula leader.
                example.setCellValue(CsvSafe.neutralise(field.example() == null ? "" : field.example()));

                data.setColumnWidth(i, Math.min(60, Math.max(14, field.label().length() + 4)) * 256);
            }
            data.createFreezePane(0, 1);

            writeDocsSheet(workbook, type, fields);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build the import template", e);
        }
    }

    /** The .csv: header row plus the example row. Formula-escaped, see {@link CsvSafe}. */
    public byte[] csv(List<ImportFieldDescriptor> fields) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(fields.stream().map(ImportFieldDescriptor::label).toList());
        rows.add(fields.stream().map(f -> f.example() == null ? "" : f.example()).toList());
        return CsvSafe.document(rows);
    }

    public String filename(ImportEntityType type, String format) {
        return "assetiq-" + type.slug() + "-import-template." + ("csv".equals(format) ? "csv" : "xlsx");
    }

    // ── Documentation sheet ───────────────────────────────────────────────────

    private void writeDocsSheet(Workbook workbook, ImportEntityType type, List<ImportFieldDescriptor> fields) {
        Sheet docs = workbook.createSheet(DOCS_SHEET);
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle wrap = workbook.createCellStyle();
        wrap.setWrapText(true);
        wrap.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.TOP);

        Row intro = docs.createRow(0);
        intro.createCell(0).setCellValue(
                "Fill in the '" + DATA_SHEET + "' sheet. Row 2 is an example — replace or delete it. "
                        + "Column order does not matter: you map your columns to these fields when you upload.");

        String[] columns = {"Column", "Field name", "Required?", "Type", "Allowed values", "Format / notes",
                "Also recognised as"};
        Row header = docs.createRow(2);
        for (int i = 0; i < columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIndex = 3;
        for (ImportFieldDescriptor field : fields) {
            Row row = docs.createRow(rowIndex++);
            setText(row, 0, field.label(), wrap);
            setText(row, 1, field.name(), wrap);
            setText(row, 2, field.required() ? "Required" : "Optional", wrap);
            setText(row, 3, typeLabel(field), wrap);
            setText(row, 4, String.join(", ", field.enumValues()), wrap);
            setText(row, 5, field.notes(), wrap);
            setText(row, 6, String.join(", ", field.aliases()), wrap);
        }

        docs.setColumnWidth(0, 30 * 256);
        docs.setColumnWidth(1, 26 * 256);
        docs.setColumnWidth(2, 12 * 256);
        docs.setColumnWidth(3, 14 * 256);
        docs.setColumnWidth(4, 46 * 256);
        docs.setColumnWidth(5, 56 * 256);
        docs.setColumnWidth(6, 46 * 256);
        docs.createFreezePane(0, 3);

        Row footer = docs.createRow(rowIndex + 1);
        footer.createCell(0).setCellValue("Importing: " + type.label() + " — " + type.description());
    }

    private static String typeLabel(ImportFieldDescriptor field) {
        return switch (field.dataType()) {
            case STRING -> "Text";
            case TEXT -> "Long text";
            case INTEGER -> "Whole number";
            case DECIMAL -> "Number";
            case DATE -> "Date";
            case BOOLEAN -> "Yes / No";
            case ENUM -> "One of";
            case EMAIL -> "Email";
            case REFERENCE -> "Name of an existing record";
        };
    }

    private static void setText(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(CsvSafe.neutralise(value == null ? "" : value));
        cell.setCellStyle(style);
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle requiredHeaderStyle(Workbook workbook) {
        CellStyle style = headerStyle(workbook);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.DARK_RED.getIndex());
        style.setFont(font);
        return style;
    }
}
