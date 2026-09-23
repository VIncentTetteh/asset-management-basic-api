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

    // ── Enum vocabulary ───────────────────────────────────────────────────────

    @Test
    void appliesTheValueMappingTheUserChoseInTheWizard() {
        // "Working" is not an AssetCondition and never will be. The user said in the
        // wizard that it means GOOD, and that is the whole point of value mappings.
        ParsedSheet sheet = new ParsedSheet(
                List.of("Widget name", "Quantity", "State"),
                List.of(List.of("One", "1", "Working")),
                false);

        AssetImportResultDto result = engine.run(sheet,
                Map.of("name", 0, "count", 1, "condition", 2), handler,
                ImportOptions.defaults().withValueMappings(
                        Map.of("condition", Map.of("Working", "GOOD"))),
                org, 0);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getImported()).isEqualTo(1);
        assertThat(handler.created).singleElement()
                .satisfies(row -> assertThat(row.get("condition")).isEqualTo("GOOD"));
        assertThat(result.getNotes()).as("a value the user translated is not a caveat").isEmpty();
    }

    @Test
    void matchesAValueTheUserSaidNothingAboutThroughTheAliasTable() {
        // Nobody mapped anything. "Brand New" still means NEW, and a migration should not
        // have to answer a dropdown for the values that are obvious.
        ParsedSheet sheet = new ParsedSheet(
                List.of("Widget name", "Quantity", "State"),
                List.of(List.of("One", "1", "Brand New")),
                false);

        AssetImportResultDto result = engine.run(sheet,
                Map.of("name", 0, "count", 1, "condition", 2), handler,
                ImportOptions.defaults(), org, 0);

        assertThat(result.getErrors()).isEmpty();
        assertThat(handler.created).singleElement()
                .satisfies(row -> assertThat(row.get("condition")).isEqualTo("NEW"));
    }

    @Test
    void anUnrecognisableEnumValueLeavesTheFieldBlankRatherThanFailingTheRow() {
        // This is the failure the whole change exists to remove: a file refused because
        // one column speaks a vocabulary AssetIQ has never heard of. The row imports,
        // the field is blank, and the note says exactly which cell and why.
        ParsedSheet sheet = new ParsedSheet(
                List.of("Widget name", "Quantity", "State"),
                List.of(List.of("One", "1", "Gently Loved")),
                false);

        AssetImportResultDto result = engine.run(sheet,
                Map.of("name", 0, "count", 1, "condition", 2), handler,
                ImportOptions.defaults(), org, 0);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getFailed()).isZero();
        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getOutcome()).isEqualTo(AssetImportResultDto.Outcome.SUCCESS);
        assertThat(handler.created).singleElement()
                .satisfies(row -> assertThat(row.get("condition")).isNull());
        assertThat(result.getNotes()).singleElement().satisfies(note -> {
            assertThat(note.getRow()).isEqualTo(2);
            assertThat(note.getField()).isEqualTo("condition");
            assertThat(note.getColumn()).isEqualTo("State");
            assertThat(note.getValue()).isEqualTo("Gently Loved");
            assertThat(note.getMessage()).contains("left blank");
        });
    }

    @Test
    void aValueTheUserMarkedIgnoredLeavesTheFieldBlankAndSaysSo() {
        ParsedSheet sheet = new ParsedSheet(
                List.of("Widget name", "Quantity", "State"),
                List.of(List.of("One", "1", "n/a")),
                false);

        AssetImportResultDto result = engine.run(sheet,
                Map.of("name", 0, "count", 1, "condition", 2), handler,
                ImportOptions.defaults().withValueMappings(
                        Map.of("condition", Map.of("n/a", ImportValueMappings.IGNORE))),
                org, 0);

        assertThat(result.getErrors()).isEmpty();
        assertThat(handler.created).singleElement()
                .satisfies(row -> assertThat(row.get("condition")).isNull());
        assertThat(result.getNotes()).singleElement()
                .satisfies(note -> assertThat(note.getMessage()).contains("ignored"));
    }

    @Test
    void aTargetThisEnumDoesNotHaveCostsTheFieldAndNotTheRow() {
        // A client bug -- a stale dropdown, a renamed constant -- is not the user's
        // spreadsheet, and must not cost them 3000 rows.
        ParsedSheet sheet = new ParsedSheet(
                List.of("Widget name", "Quantity", "State"),
                List.of(List.of("One", "1", "Working")),
                false);

        AssetImportResultDto result = engine.run(sheet,
                Map.of("name", 0, "count", 1, "condition", 2), handler,
                ImportOptions.defaults().withValueMappings(
                        Map.of("condition", Map.of("Working", "PRISTINE"))),
                org, 0);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getNotes()).singleElement()
                .satisfies(note -> assertThat(note.getMessage()).contains("PRISTINE"));
    }

    @Test
    void notesFromAFailedRowAreNotReported() {
        // The row carries an unreadable number and an unrecognisable state. It fails on
        // the number; reporting "we also blanked your state column" describes work on a
        // record that was never written.
        ParsedSheet sheet = new ParsedSheet(
                List.of("Widget name", "Quantity", "State"),
                List.of(List.of("One", "nope", "Gently Loved")),
                false);

        AssetImportResultDto result = engine.run(sheet,
                Map.of("name", 0, "count", 1, "condition", 2), handler,
                ImportOptions.defaults(), org, 0);

        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getNotes()).isEmpty();
    }

    // ── Counting ──────────────────────────────────────────────────────────────

    @Test
    void theCountsAndTheErrorListAgreeWithEachOther() {
        // The bug this pins: a result screen showing "ROWS 1, IMPORTED 0, SKIPPED 1"
        // above a list saying "2 rows were not imported". One row cannot be two
        // failures, and a run that wrote nothing is not a success.
        handler.existing.add("dupe");
        ParsedSheet sheet = new ParsedSheet(
                List.of("Widget name", "Quantity"),
                List.of(
                        List.of("Good", "1"),
                        List.of("Dupe", "2"),     // duplicate, deliberately skipped
                        List.of("", "3"),         // required field blank
                        List.of("Bad", "nope")),  // not a number
                false);

        AssetImportResultDto result = engine.run(sheet, Map.of("name", 0, "count", 1),
                handler, ImportOptions.defaults(), org, 0);

        assertThat(result.getTotalRows()).isEqualTo(4);
        assertThat(result.getImported() + result.getUpdated() + result.getSkipped())
                .as("every row read is accounted for exactly once")
                .isEqualTo(result.getTotalRows());
        assertThat(result.getErrors())
                .as("one error per failed row, and nothing else")
                .hasSize(result.getFailed());
        assertThat(result.getFailed()).isEqualTo(2);
        assertThat(result.getDuplicatesSkipped()).isEqualTo(1);
        assertThat(result.getErrors()).noneMatch(e -> e.getRow() == 0);
        assertThat(result.getOutcome()).isEqualTo(AssetImportResultDto.Outcome.PARTIAL);
    }

    @Test
    void anImportThatWroteNothingIsNotASuccess() {
        ParsedSheet sheet = new ParsedSheet(
                List.of("Widget name", "Quantity"),
                List.of(List.of("", "1")),
                false);

        AssetImportResultDto result = engine.run(sheet, Map.of("name", 0, "count", 1),
                handler, ImportOptions.defaults(), org, 0);

        assertThat(result.getImported()).isZero();
        assertThat(result.getOutcome()).isEqualTo(AssetImportResultDto.Outcome.FAILED);
    }

    @Test
    void anEmptyFileSaysSoRatherThanClaimingSuccess() {
        ParsedSheet sheet = new ParsedSheet(List.of("Widget name", "Quantity"), List.of(), false);

        AssetImportResultDto result = engine.run(sheet, Map.of("name", 0, "count", 1),
                handler, ImportOptions.defaults(), org, 0);

        assertThat(result.getOutcome()).isEqualTo(AssetImportResultDto.Outcome.NOTHING_TO_IMPORT);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void aTruncatedFileIsAStateOfTheRunAndNotAFailedRow() {
        ParsedSheet sheet = new ParsedSheet(
                List.of("Widget name", "Quantity"),
                List.of(List.of("One", "1")),
                true);

        AssetImportResultDto result = engine.run(sheet, Map.of("name", 0, "count", 1),
                handler, ImportOptions.defaults(), org, 0);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getFailed()).isZero();
        assertThat(result.isStoppedEarly()).isTrue();
        assertThat(result.getStoppedReason()).contains("Split the file");
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
    void aColumnTheUserChoseToKeepReachesTheHandlerAsACustomField() {
        // The other half of the same choice: "ignore" and "create as a custom field" are
        // both legitimate answers to a column the product has no field for, and the user
        // makes the call per column in the wizard.
        ParsedSheet sheet = new ParsedSheet(
                List.of("Widget name", "Quantity", "Cost Centre Ref", "Floor"),
                List.of(List.of("One", "1", "CC-1", "3")),
                false);

        AssetImportResultDto result = engine.run(sheet, Map.of("name", 0, "count", 1),
                handler, ImportOptions.defaults().withCustomFieldColumns(java.util.Set.of(2)),
                org, 0);

        assertThat(result.getImported()).isEqualTo(1);
        assertThat(handler.unmappedSeen)
                .as("the chosen column arrives; the one left on ignore does not")
                .containsExactly(Map.of("Cost Centre Ref", "CC-1"));
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
                    ImportFieldDescriptor.field("count", "Quantity", ImportDataType.INTEGER).build(),
                    ImportFieldDescriptor.enumField("condition", "Condition",
                            com.assetiq.enums.AssetCondition.class).build());
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
                    com.assetiq.enums.AssetCondition condition =
                            row.enumValue(com.assetiq.enums.AssetCondition.class, "condition");
                    payload.put("condition", condition == null ? null : condition.name());
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
