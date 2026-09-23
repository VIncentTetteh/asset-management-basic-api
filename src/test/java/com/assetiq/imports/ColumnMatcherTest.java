package com.assetiq.imports;

import com.assetiq.imports.handlers.AssetImportHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auto-mapping.
 *
 * <p>The customer this framework exists for exports from a different ITAM platform, so
 * the realistic test is a foreign header set in a foreign order — not AssetIQ's own
 * template with the columns shuffled.</p>
 */
class ColumnMatcherTest {

    private final ColumnMatcher matcher = new ColumnMatcher();

    private List<ImportFieldDescriptor> assetFields() {
        return new AssetImportHandler(null, null, null, null, null, null, null, null).fields();
    }

    @Test
    void mapsARealisticForeignPlatformExport() {
        // Shape of a Snipe-IT / AssetTiger style export: different words, different
        // order, different punctuation, and columns AssetIQ has no field for.
        List<String> headers = List.of(
                "Asset Tag",            // -> assetTag        (normalised label)
                "Item Name",            // -> name            (alias)
                "Serial",               // -> serialNumber    (alias)
                "Manufacturer",         // -> manufacturer    (exact label)
                "Model No.",            // -> model           (alias "model number")
                "Category",             // -> category        (exact label)
                "Location",             // -> location        (exact label)
                "Assigned To",          // -> assignedUserEmail (alias "assigned to")
                "Purchase Date",        // -> purchaseDate    (exact label)
                "Purchase Cost",        // -> purchaseCost    (exact label)
                "Warranty Expiration",  // -> warrantyExpiryDate (alias)
                "Checkout Notes",       // -> nothing
                "EOL Date");            // -> nothing

        Map<String, Integer> mapping = matcher.suggest(assetFields(), headers);

        assertThat(mapping).containsEntry("assetTag", 0);
        assertThat(mapping).containsEntry("name", 1);
        assertThat(mapping).containsEntry("serialNumber", 2);
        assertThat(mapping).containsEntry("manufacturer", 3);
        assertThat(mapping).containsEntry("category", 5);
        assertThat(mapping).containsEntry("location", 6);
        assertThat(mapping).containsEntry("assignedUserEmail", 7);
        assertThat(mapping).containsEntry("purchaseDate", 8);
        assertThat(mapping).containsEntry("purchaseCost", 9);
        assertThat(mapping).containsEntry("warrantyExpiryDate", 10);

        assertThat(matcher.unmappedColumns(headers, mapping))
                .contains("Checkout Notes", "EOL Date");
        assertThat(matcher.missingRequiredFields(assetFields(), mapping)).isEmpty();
    }

    @Test
    void ignoresCaseWhitespaceAndPunctuation() {
        List<String> headers = List.of("ASSET_TAG", "  asset name  ", "serial-number");
        Map<String, Integer> mapping = matcher.suggest(assetFields(), headers);

        assertThat(mapping).containsEntry("assetTag", 0);
        assertThat(mapping).containsEntry("name", 1);
        assertThat(mapping).containsEntry("serialNumber", 2);
    }

    @Test
    void leavesAnUnrecognisedColumnUnmappedRatherThanGuessing() {
        List<String> headers = List.of("Asset Name", "Widget Code 7", "Blorp");
        Map<String, Integer> mapping = matcher.suggest(assetFields(), headers);

        assertThat(mapping).containsEntry("name", 0);
        assertThat(mapping.values()).doesNotContain(1, 2);
        assertThat(matcher.unmappedColumns(headers, mapping)).containsExactly("Widget Code 7", "Blorp");
    }

    @Test
    void doesNotMapEitherColumnWhenTwoCarryTheSameHeader() {
        // Duplicated headers are common in hand-edited exports. Picking one at random
        // silently discards a column's worth of data.
        List<String> headers = List.of("Asset Name", "Asset Name", "Serial");
        Map<String, Integer> mapping = matcher.suggest(assetFields(), headers);

        // Neither is offered: the wizard asks, rather than silently discarding one
        // column's worth of data.
        assertThat(mapping).doesNotContainKey("name");
        assertThat(mapping).containsEntry("serialNumber", 2);
        assertThat(matcher.missingRequiredFields(assetFields(), mapping)).containsExactly("name");
    }

    @Test
    void reportsRequiredFieldsThatNothingMapsTo() {
        List<String> headers = List.of("Serial", "Manufacturer");
        Map<String, Integer> mapping = matcher.suggest(assetFields(), headers);

        assertThat(matcher.missingRequiredFields(assetFields(), mapping)).containsExactly("name");
    }

    @Test
    void anExactHeaderWinsOverAnAliasOnAnotherField() {
        // "Description" is the asset description's label and also an alias of "name".
        // The exact pass must settle it, or the asset name column gets the description.
        List<String> headers = List.of("Asset name", "Description");
        Map<String, Integer> mapping = matcher.suggest(assetFields(), headers);

        assertThat(mapping).containsEntry("name", 0);
        assertThat(mapping).containsEntry("description", 1);
    }
}
