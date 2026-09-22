package com.assetiq.assets;

import com.assetiq.models.Category;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryAssetDefaultsTest {

    @Test
    void firstTagForAPrefix() {
        assertThat(CategoryAssetDefaults.nextTag("LAP", List.of())).isEqualTo("LAP-0001");
    }

    @Test
    void nextTagFollowsTheHighestNumberAndIgnoresLookalikes() {
        List<String> existing = Arrays.asList("LAP-0001", "LAP-0009", "LAPTOP-0050", "LAP-X1", "LAP-12", null);

        assertThat(CategoryAssetDefaults.nextTag("LAP", existing)).isEqualTo("LAP-0013");
    }

    @Test
    void numbersGrowPastThePaddingWidth() {
        assertThat(CategoryAssetDefaults.nextTag("IT", List.of("IT-9999"))).isEqualTo("IT-10000");
    }

    @Test
    void prefixIsTrimmedAndBlankMeansNone() {
        Category c = new Category();
        c.setAssetPrefixCode("  LAP ");
        assertThat(CategoryAssetDefaults.prefixOf(c)).isEqualTo("LAP");
        c.setAssetPrefixCode("  ");
        assertThat(CategoryAssetDefaults.prefixOf(c)).isNull();
    }

    @Test
    void warrantyRunsFromThePurchaseDate() {
        Category c = new Category();
        c.setDefaultWarrantyPeriodMonths(24);

        assertThat(CategoryAssetDefaults.warrantyExpiry(c, LocalDate.of(2026, 1, 31))).isEqualTo(LocalDate.of(2028, 1, 31));
        assertThat(CategoryAssetDefaults.warrantyExpiry(c, null)).isNull();
        c.setDefaultWarrantyPeriodMonths(0);
        assertThat(CategoryAssetDefaults.warrantyExpiry(c, LocalDate.of(2026, 1, 31))).isNull();
    }
}
