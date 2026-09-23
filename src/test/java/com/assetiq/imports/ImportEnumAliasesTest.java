package com.assetiq.imports;

import com.assetiq.enums.AssetCondition;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.AssetType;
import com.assetiq.enums.LicenseStatus;
import com.assetiq.enums.SupplierStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The alias table, which is the difference between "Asset type 'Laptop' is not a valid
 * value" and an import that works.
 *
 * <p>Two properties matter and both are pinned here. It must recognise the vocabulary
 * real exports actually use, and it must <em>not</em> guess: a wrong suggestion is
 * pre-selected in a dropdown across three thousand rows and nobody notices.</p>
 */
class ImportEnumAliasesTest {

    @Test
    void readsTheWordsAnAssetRegisterActuallyUses() {
        assertThat(ImportEnumAliases.resolve(AssetType.class, "Laptop")).contains(AssetType.HARDWARE);
        assertThat(ImportEnumAliases.resolve(AssetType.class, "Monitor")).contains(AssetType.HARDWARE);
        assertThat(ImportEnumAliases.resolve(AssetType.class, "PC")).contains(AssetType.HARDWARE);
        assertThat(ImportEnumAliases.resolve(AssetType.class, "IT Equipment")).contains(AssetType.HARDWARE);
        assertThat(ImportEnumAliases.resolve(AssetType.class, "App")).contains(AssetType.SOFTWARE);
        assertThat(ImportEnumAliases.resolve(AssetType.class, "Licence")).contains(AssetType.SOFTWARE);
        assertThat(ImportEnumAliases.resolve(AssetType.class, "Van")).contains(AssetType.VEHICLE);
        assertThat(ImportEnumAliases.resolve(AssetType.class, "Desk")).contains(AssetType.FURNITURE);
        assertThat(ImportEnumAliases.resolve(AssetType.class, "Generator")).contains(AssetType.EQUIPMENT);
    }

    @Test
    void ignoresCasePunctuationAndSpacing() {
        assertThat(ImportEnumAliases.resolve(AssetStatus.class, "in use")).contains(AssetStatus.IN_USE);
        assertThat(ImportEnumAliases.resolve(AssetStatus.class, "IN-USE")).contains(AssetStatus.IN_USE);
        assertThat(ImportEnumAliases.resolve(AssetStatus.class, "  In_Use ")).contains(AssetStatus.IN_USE);
        assertThat(ImportEnumAliases.resolve(AssetStatus.class, "In Service")).contains(AssetStatus.IN_USE);
    }

    @Test
    void readsTheSameWordDifferentlyPerEnum() {
        // "Active" is a supplier that is approved and a licence that is valid. Keying the
        // table per enum is what keeps one from being read as the other.
        assertThat(ImportEnumAliases.resolve(SupplierStatus.class, "Approved"))
                .contains(SupplierStatus.ACTIVE);
        assertThat(ImportEnumAliases.resolve(LicenseStatus.class, "Due for renewal"))
                .contains(LicenseStatus.EXPIRING_SOON);
        assertThat(ImportEnumAliases.resolve(SupplierStatus.class, "Due for renewal")).isEmpty();
    }

    @Test
    void declinesToGuess() {
        // No fuzzy pass, on purpose. "Retired" is not "Reserved", and the user is better
        // served by an empty dropdown than by a plausible wrong answer.
        assertThat(ImportEnumAliases.resolve(AssetCondition.class, "Gently Loved")).isEmpty();
        assertThat(ImportEnumAliases.resolve(AssetType.class, "Widget")).isEmpty();
        assertThat(ImportEnumAliases.resolve(AssetStatus.class, "")).isEmpty();
        assertThat(ImportEnumAliases.resolve(AssetStatus.class, null)).isEmpty();
    }

    @Test
    void neverSuggestsAConstantTheFieldDoesNotHave() {
        // The suggestion path takes the allowed list from the descriptor rather than the
        // class, so a field that narrows its enum cannot be handed a constant it refuses.
        List<String> narrowed = List.of("HARDWARE", "OTHER");
        assertThat(ImportEnumAliases.suggest("AssetType", narrowed, "Laptop")).contains("HARDWARE");
        assertThat(ImportEnumAliases.suggest("AssetType", narrowed, "Van")).isEmpty();
    }

    @Test
    void everyConstantResolvesToItself() {
        for (Class<? extends Enum<?>> type : List.of(AssetType.class, AssetStatus.class,
                AssetCondition.class, SupplierStatus.class, LicenseStatus.class)) {
            List<String> allowed = Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
            for (String constant : allowed) {
                assertThat(ImportEnumAliases.suggest(type.getSimpleName(), allowed, constant))
                        .as("%s.%s", type.getSimpleName(), constant)
                        .contains(constant);
            }
        }
    }
}
