package com.assetiq.imports;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The template is generated from the descriptors, and these tests are the guarantee
 * that it stays that way.
 *
 * <p>A template that lists columns the importer does not accept, or omits ones it
 * requires, is worse than no template: it teaches the customer a format the product
 * then rejects. So the assertion is not "the header row looks right" but "the header
 * row IS the descriptor list", computed from the same source.</p>
 */
class ImportTemplateGeneratorTest {

    private final ImportTemplateGenerator generator = new ImportTemplateGenerator();

    private static final List<ImportFieldDescriptor> FIELDS = List.of(
            ImportFieldDescriptor.field("name", "Thing name", ImportDataType.STRING).required()
                    .example("Widget").notes("Must be unique.").aliases("title", "label").build(),
            ImportFieldDescriptor.field("count", "How many", ImportDataType.INTEGER)
                    .example("=1+1").notes("Whole number.").build(),
            ImportFieldDescriptor.field("kind", "Kind", ImportDataType.ENUM)
                    .values(List.of("ALPHA", "BETA")).example("ALPHA").build()
    );

    @Test
    void xlsxHeaderRowIsExactlyTheDescriptorLabels() throws Exception {
        byte[] bytes = generator.xlsx(ImportEntityType.ASSETS, FIELDS);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet data = workbook.getSheetAt(0);
            assertThat(headerTexts(data.getRow(0)))
                    .isEqualTo(FIELDS.stream().map(ImportFieldDescriptor::label).toList());
        }
    }

    @Test
    void xlsxCarriesOneExampleRow() throws Exception {
        byte[] bytes = generator.xlsx(ImportEntityType.ASSETS, FIELDS);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet data = workbook.getSheetAt(0);
            assertThat(data.getLastRowNum()).isEqualTo(1);
            assertThat(data.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Widget");
        }
    }

    @Test
    void secondSheetDocumentsEveryColumn() throws Exception {
        byte[] bytes = generator.xlsx(ImportEntityType.ASSETS, FIELDS);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            Sheet docs = workbook.getSheetAt(1);

            assertThat(headerTexts(docs.getRow(2)))
                    .containsExactly("Column", "Field name", "Required?", "Type",
                            "Allowed values", "Format / notes", "Also recognised as");

            // One documentation row per field, carrying required-ness, the allowed
            // values and the format note.
            Row nameRow = docs.getRow(3);
            assertThat(nameRow.getCell(0).getStringCellValue()).isEqualTo("Thing name");
            assertThat(nameRow.getCell(1).getStringCellValue()).isEqualTo("name");
            assertThat(nameRow.getCell(2).getStringCellValue()).isEqualTo("Required");
            assertThat(nameRow.getCell(5).getStringCellValue()).isEqualTo("Must be unique.");
            assertThat(nameRow.getCell(6).getStringCellValue()).isEqualTo("title, label");

            Row kindRow = docs.getRow(5);
            assertThat(kindRow.getCell(2).getStringCellValue()).isEqualTo("Optional");
            assertThat(kindRow.getCell(4).getStringCellValue()).isEqualTo("ALPHA, BETA");
        }
    }

    @Test
    void everyRealEntityTypeProducesATemplateMatchingItsDescriptors() throws Exception {
        // The registry-wide version of the first test: no entity type may ship a
        // template that disagrees with what its handler validates.
        for (ImportEntityType type : ImportEntityType.values()) {
            List<ImportFieldDescriptor> fields = ImportTestDescriptors.fieldsFor(type);
            if (fields.isEmpty()) continue;
            byte[] bytes = generator.xlsx(type, fields);
            try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                assertThat(headerTexts(workbook.getSheetAt(0).getRow(0)))
                        .as("template header row for %s", type.slug())
                        .isEqualTo(fields.stream().map(ImportFieldDescriptor::label).toList());
            }
        }
    }

    @Test
    void csvTemplateIsHeaderRowPlusExampleAndEscapesFormulas() {
        String csv = new String(generator.csv(FIELDS), StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");

        assertThat(lines[0]).isEqualTo("﻿Thing name,How many,Kind");
        // The example for "count" is "=1+1"; it must not reach a spreadsheet as a formula.
        assertThat(lines[1]).isEqualTo("Widget,'=1+1,ALPHA");
    }

    @Test
    void xlsxExampleCellsAreAlsoFormulaNeutralised() throws Exception {
        byte[] bytes = generator.xlsx(ImportEntityType.ASSETS, FIELDS);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo("'=1+1");
        }
    }

    @Test
    void filenameSaysWhatItIsAndInWhichFormat() {
        assertThat(generator.filename(ImportEntityType.SOFTWARE_LICENCES, "xlsx"))
                .isEqualTo("assetiq-licenses-import-template.xlsx");
        assertThat(generator.filename(ImportEntityType.SUPPLIERS, "csv"))
                .isEqualTo("assetiq-suppliers-import-template.csv");
    }

    private static List<String> headerTexts(Row row) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < row.getLastCellNum(); i++) {
            values.add(row.getCell(i).getStringCellValue());
        }
        return values;
    }
}
