package com.assetiq.assets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssetLabelsTest {

    @Test
    void omitsMissingTag() {
        assertThat(AssetLabels.describe("Laptop", null)).isEqualTo("Asset 'Laptop'");
        assertThat(AssetLabels.describe("Laptop", "  ")).isEqualTo("Asset 'Laptop'");
        assertThat(AssetLabels.tagClause(null)).isEmpty();
    }

    @Test
    void includesPresentTag() {
        assertThat(AssetLabels.describe("Laptop", "IT-1")).isEqualTo("Asset 'Laptop' (tag: IT-1)");
        assertThat(AssetLabels.tagClause("IT-1")).isEqualTo(" (tag: IT-1)");
    }
}
