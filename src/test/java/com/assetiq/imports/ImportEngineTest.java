package com.assetiq.imports;

import com.assetiq.dto.AssetImportResultDto;
import com.assetiq.models.Organisation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The engine, exercised through a stub handler so the assertions are about the engine
 * rather than about any one entity type.
 *
 * <p>What matters here: a mapping the user hand-edited is honoured literally; a dry run
 * writes nothing; and a failed row names the row, the user's own header and the
 * problem.</p>
 */
class ImportEngineTest {

    /** A transaction manager that does nothing, so row isolation is exercised without a DB. */
    private static final PlatformTransactionManager NO_OP_TX = new PlatformTransactionManager() {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }
        @Override public void commit(TransactionStatus status) { }
        @Override public void rollback(TransactionStatus status) { }
    };

    private ImportEngine engine;
    private StubHandler handler;
    private Organisation org;

    @BeforeEach
    void setUp() {
        engine = new ImportEngine(NO_OP_TX);
        handler = new StubHandler();
        org = new Organisation();
        org.setId(UUID.randomUUID());
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    @Test
    void honoursAHandEditedMappingRatherThanColumnOrder() {
        // The file's columns are in an order nothing would guess, and the user has told
        // us which is which. The engine must read them exactly as instructed.
        ParsedSheet sheet = new ParsedSheet(
                List.of("Col X", "Col Y", "Col Z"),
                List.of(List.of("42", "Widget", "ignored")),
                false);

        AssetImportResultDto result = engine.run(sheet,
                Map.of("name", 1, "count", 0), handler, ImportOptions.defaults(), org, 0);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getImported()).isEqualTo(1);
        assertThat(handler.created).singleElement().satisfies(row -> {
            assertThat(row.get("name")).isEqualTo("Widget");
            assertThat(row.get("count")).isEqualTo("42");
        });
    }

    @Test
    void refusesAMappingThatLeavesARequiredFieldUnmapped() {
        ParsedSheet sheet = new ParsedSheet(List.of("Col X"), List.of(List.of("Widget")), false);

        AssetImportResultDto result = engine.run(sheet,
                Map.of("count", 0), handler, ImportOptions.defaults(), org, 0);

        assertThat(handler.created).isEmpty();
        // A mapping problem is a problem with the request, not with a row: it must not
        // appear in the error list, where its length has to match the failure count.
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getFatalError()).contains("required fields are not mapped").contains("name");
        assertThat(result.getOutcome()).isEqualTo(AssetImportResultDto.Outcome.FAILED);
    }

    @Test
    void refusesTwoFieldsPointedAtOneColumn() {
        ParsedSheet sheet = new ParsedSheet(List.of("Col X"), List.of(List.of("Widget")), false);

        Map<String, Integer> mapping = new LinkedHashMap<>();
        mapping.put("name", 0);
        mapping.put("count", 0);
        AssetImportResultDto result = engine.run(sheet, mapping, handler, ImportOptions.defaults(), org, 0);

        assertThat(handler.created).isEmpty();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getFatalError()).contains("mapped to both");
        assertThat(result.getOutcome()).isEqualTo(AssetImportResultDto.Outcome.FAILED);
    }

    // ── Preview ───────────────────────────────────────────────────────────────

    @Test
    void previewValidatesWithoutWritingAndReportsEveryBadRow() {
        ParsedSheet sheet = new ParsedSheet(
                List.of("Widget name", "Quantity"),
                List.of(
                        List.of("Good one", "1"),
                        List.of("", "2"),            // required field blank
                        List.of("Bad number", "xx"), // not an integer
                        List.of("Also good", "3")),
                false);

        AssetImportResultDto result = engine.run(sheet, Map.of("name", 0, "count", 1),
                handler, ImportOptions.defaults().withDryRun(true), org, 100);

        assertThat(handler.created).as("a dry run must not write").isEmpty();
        assertThat(result.getTotalRows()).isEqualTo(4);
        assertThat(result.getImported()).isEqualTo(2);
        assertThat(result.getSkipped()).isEqualTo(2);
        assertThat(result.getErrors()).hasSize(2);
    }

    @Test
    void aRowErrorNamesTheRowTheUsersOwnHeaderAndTheProblem() {
        ParsedSheet sheet = new ParsedSheet(
                List.of("Widget name", "Quantity"),
                List.of(List.of("Fine", "not-a-number")),
                false);

        AssetImportResultDto result = engine.run(sheet, Map.of("name", 0, "count", 1),
                handler, ImportOptions.defaults(), org, 0);

        assertThat(result.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getRow()).isEqualTo(2);                 // header is row 1
            assertThat(error.getColumn()).isEqualTo("Quantity");     // the user's word
            assertThat(error.getField()).isEqualTo("count");         // ours, for the UI
            assertThat(error.getMessage())
                    .contains("Quantity")
                    .contains("whole number")
                    .contains("not-a-number");
        });
    }

    @Test
    void previewStopsAtTheRowLimit() {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < 50; i++) rows.add(List.of("Widget " + i, "1"));
        ParsedSheet sheet = new ParsedSheet(List.of("Widget name", "Quantity"), rows, false);

        AssetImportResultDto result = engine.run(sheet, Map.of("name", 0, "count", 1),
                handler, ImportOptions.defaults().withDryRun(true), org, 10);

        assertThat(result.getTotalRows()).isEqualTo(10);
    }

    // ── Rows ──────────────────────────────────────────────────────────────────

    @Test
    void aBlankLineInTheMiddleIsNotAFailedRow() {
        ParsedSheet sheet = new ParsedSheet(
                List.of("Widget name", "Quantity"),
                List.of(List.of("One", "1"), List.of("", ""), List.of("Two", "2")),
                false);

        AssetImportResultDto result = engine.run(sheet, Map.of("name", 0, "count", 1),
                handler, ImportOptions.defaults(), org, 0);

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getImported()).isEqualTo(2);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void oneBadRowDoesNotStopTheRest() {
        ParsedSheet sheet = new ParsedSheet(
                List.of("Widget name", "Quantity"),
                List.of(List.of("One", "1"), List.of("Two", "nope"), List.of("Three", "3")),
                false);

        AssetImportResultDto result = engine.run(sheet, Map.of("name", 0, "count", 1),
                handler, ImportOptions.defaults(), org, 0);

        assertThat(result.getImported()).isEqualTo(2);
        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(handler.created).hasSize(2);
    }

    @Test
    void skipInvalidRowsOffStopsAtTheFirstBadRowAndSaysSo() {
        ParsedSheet sheet = new ParsedSheet(
                List.of("Widget name", "Quantity"),
                List.of(List.of("One", "1"), List.of("Two", "nope"), List.of("Three", "3")),
                false);

        AssetImportResultDto result = engine.run(sheet, Map.of("name", 0, "count", 1),
                handler, ImportOptions.defaults().withSkipInvalidRows(false), org, 0);

        assertThat(handler.created).hasSize(1);
        assertThat(result.getImported()).isEqualTo(1);
        // Stopping early is a state of the run, not an extra failed row. The one error
        // is the row that actually failed, and the stop is explained beside it.
        assertThat(result.getErrors()).singleElement()
                .satisfies(e -> assertThat(e.getRow()).isEqualTo(3));
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.isStoppedEarly()).isTrue();
        assertThat(result.getStoppedReason())
                .contains("Stopped at row 3")
                .contains("remain imported");
        assertThat(result.getOutcome()).isEqualTo(AssetImportResultDto.Outcome.PARTIAL);
    }

    // ── Unmapped columns ──────────────────────────────────────────────────────

    @Test
    void anUnmappedColumnIsIgnoredOnTheMappingDrivenPath() {
        // The premise of the wizard: a sheet from another platform carries columns
        // AssetIQ has no field for, and the answer to them is to ignore them. This used
        // to force them through the legacy custom-field rule, failing every row of an
        // otherwise perfect file.
        ParsedSheet sheet = new ParsedSheet(
                List.of("Widget name", "Quantity", "Cost Centre Ref"),
                List.of(List.of("One", "1", "CC-1")),
                false);

        AssetImportResultDto result = engine.run(sheet, Map.of("name", 0, "count", 1),
                handler, ImportOptions.defaults(), org, 0);

        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getErrors()).isEmpty();
        assertThat(handler.unmappedSeen).containsExactly(Map.of());
    }

    @Test
    void theLegacyPathStillSeesUnmappedColumns() {
        // captureUnmappedColumns is true only for the positional asset import, where
        // extra columns have always become custom fields behind a feature flag.
        ParsedSheet sheet = new ParsedSheet(
                List.of("Widget name", "Quantity", "Colour"),
                List.of(List.of("One", "1", "Blue")),
                false);

        AssetImportResultDto result = engine.run(sheet, Map.of("name", 0, "count", 1),
                handler, ImportOptions.defaults().withCaptureUnmappedColumns(true), org, 0);

        assertThat(result.getImported()).isEqualTo(1);
        assertThat(handler.unmappedSeen).containsExactly(Map.of("Colour", "Blue"));
    }

    // ── Duplicates ────────────────────────────────────────────────────────────

    @Test
    void duplicateSkipLeavesTheExistingRecordAlone() {
        handler.existing.add("one");
        AssetImportResultDto result = runTwoRowsWith(ImportOptions.DuplicateStrategy.SKIP);

        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getUpdated()).isZero();
        assertThat(handler.updated).isEmpty();
    }

    @Test
    void duplicateUpdatePatchesTheExistingRecord() {
        handler.existing.add("one");
        AssetImportResultDto result = runTwoRowsWith(ImportOptions.DuplicateStrategy.UPDATE);

        assertThat(result.getUpdated()).isEqualTo(1);
        assertThat(result.getImported()).isEqualTo(1);
        assertThat(handler.updated).containsExactly("one");
    }

    @Test
    void duplicateFailReportsTheRowAndNamesTheClashingValue() {
        handler.existing.add("one");
        AssetImportResultDto result = runTwoRowsWith(ImportOptions.DuplicateStrategy.FAIL);

        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getErrors()).singleElement()
                .satisfies(e -> assertThat(e.getMessage()).contains("already exists").contains("one"));
    }

    @Test
    void aSecondIdenticalRowInTheSameFileIsSeenAsADuplicate() {
        ParsedSheet sheet = new ParsedSheet(
                List.of("Widget name", "Quantity"),
                List.of(List.of("Same", "1"), List.of("Same", "2")),
                false);

        AssetImportResultDto result = engine.run(sheet, Map.of("name", 0, "count", 1),
                handler, ImportOptions.defaults(), org, 0);

        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getSkipped()).isEqualTo(1);
    }

    private AssetImportResultDto runTwoRowsWith(ImportOptions.DuplicateStrategy strategy) {
        ParsedSheet sheet = new ParsedSheet(
                List.of("Widget name", "Quantity"),
                List.of(List.of("One", "1"), List.of("Two", "2")),
                false);
        return engine.run(sheet, Map.of("name", 0, "count", 1), handler,
                new ImportOptions(strategy, false, false), org, 0);
    }

    // ── Stub handler ──────────────────────────────────────────────────────────

    /** Two fields, one required, no dependencies: the engine under a microscope. */
    private static final class StubHandler implements ImportEntityHandler {

        final List<Map<String, String>> created = new ArrayList<>();
        final List<Map<String, String>> unmappedSeen = new ArrayList<>();
        final List<String> updated = new ArrayList<>();
        final List<String> existing = new ArrayList<>();

        @Override
        public ImportEntityType entityType() {
            return ImportEntityType.ASSETS;
        }

        @Override
        public List<ImportFieldDescriptor> fields() {
            return List.of(
                    ImportFieldDescriptor.field("name", "Widget name", ImportDataType.STRING)
                            .required().build(),
                    ImportFieldDescriptor.field("count", "Quantity", ImportDataType.INTEGER).build());
        }

        @Override
        public boolean unmappedColumnsBecomeCustomFields() {
            return true;
        }

        @Override
        public ImportRunner runner(Organisation organisation, ImportOptions options, ImportRunReport report) {
            return new AbstractImportRunner<Map<String, String>>(options) {
                @Override
                protected Map<String, String> build(ImportRow row) {
                    unmappedSeen.add(Map.copyOf(row.unmapped()));
                    Map<String, String> payload = new LinkedHashMap<>();
                    payload.put("name", row.requiredString("name"));
                    Integer count = row.integer("count");
                    payload.put("count", count == null ? null : String.valueOf(count));
                    return payload;
                }

                @Override
                protected java.util.UUID findExisting(ImportRow row, Map<String, String> payload) {
                    return existing.contains(payload.get("name").toLowerCase()) ? UUID.randomUUID() : null;
                }

                @Override
                protected void create(Map<String, String> payload) {
                    created.add(payload);
                    existing.add(payload.get("name").toLowerCase());
                }

                @Override
                protected void update(UUID id, Map<String, String> payload) {
                    updated.add(payload.get("name").toLowerCase());
                }

                @Override
                protected String describe() {
                    return "widget";
                }

                @Override
                protected String naturalKey(ImportRow row, Map<String, String> payload) {
                    return "name '" + payload.get("name").toLowerCase() + "'";
                }
            };
        }
    }
}
