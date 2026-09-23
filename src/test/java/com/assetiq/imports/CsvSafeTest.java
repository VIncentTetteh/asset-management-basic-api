package com.assetiq.imports;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CSV formula injection.
 *
 * <p>A cell whose text starts {@code =}, {@code +}, {@code -} or {@code @} is executed
 * by Excel, LibreOffice and Sheets when the file is opened. Every CSV this product
 * generates carries values a user typed, so this is the difference between an export
 * and a delivery mechanism.</p>
 */
class CsvSafeTest {

    @Test
    void neutralisesEveryFormulaLeader() {
        assertThat(CsvSafe.neutralise("=1+1")).isEqualTo("'=1+1");
        assertThat(CsvSafe.neutralise("+41 20 1234")).isEqualTo("'+41 20 1234");
        assertThat(CsvSafe.neutralise("-5")).isEqualTo("'-5");
        assertThat(CsvSafe.neutralise("@SUM(A1:A9)")).isEqualTo("'@SUM(A1:A9)");
    }

    @Test
    void neutralisesLeadersHiddenBehindWhitespaceControlCharacters() {
        // A leading tab or carriage return is stripped by spreadsheet parsers before the
        // formula check, so a naive startsWith("=") guard lets this straight through.
        assertThat(CsvSafe.neutralise("\t=cmd|'/c calc'!A1")).isEqualTo("'=cmd|'/c calc'!A1");
        assertThat(CsvSafe.neutralise("\r=HYPERLINK(\"http://evil\")"))
                .isEqualTo("'=HYPERLINK(\"http://evil\")");
    }

    @Test
    void leavesOrdinaryValuesAlone() {
        assertThat(CsvSafe.neutralise("Dell Latitude 5440")).isEqualTo("Dell Latitude 5440");
        assertThat(CsvSafe.neutralise("18500.00")).isEqualTo("18500.00");
        assertThat(CsvSafe.neutralise("")).isEmpty();
        assertThat(CsvSafe.neutralise(null)).isNull();
    }

    @Test
    void quotesAndEscapesWhereCsvRequiresIt() {
        String line = CsvSafe.line(List.of("plain", "has,comma", "has\"quote", "has\nnewline"));
        assertThat(line).isEqualTo("plain,\"has,comma\",\"has\"\"quote\",\"has\nnewline\"\r\n");
    }

    @Test
    void escapedFormulaSurvivesQuotingIntact() {
        String line = CsvSafe.line(List.of("=1+1,2"));
        assertThat(line).startsWith("\"'=1+1,2\"");
    }

    @Test
    void documentCarriesTheBomExcelNeedsForUtf8() {
        byte[] bytes = CsvSafe.document(List.of(List.of("Naïve"), List.of("=2+2")));
        String text = new String(bytes, StandardCharsets.UTF_8);
        assertThat(text).startsWith("﻿");
        assertThat(text).contains("Naïve");
        assertThat(text).contains("'=2+2");
    }
}
