package com.assetiq.enums;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CloudEnvironmentTest {

    @ParameterizedTest
    @CsvSource({
            "production, PROD", "Prod, PROD", " PRD , PROD", "live, PROD",
            "staging, STAGING", "stage, STAGING", "preprod, STAGING",
            "development, DEV", "dev, DEV",
            "test, TEST", "QA, TEST", "uat, TEST",
            "sandbox, OTHER", "OTHER, OTHER"
    })
    void mapsCommonSpellingsAndProviderTags(String raw, CloudEnvironment expected) {
        assertThat(CloudEnvironment.normalise(raw)).isEqualTo(expected);
    }

    @Test
    void blankIsNotSetNeverProd() {
        assertThat(CloudEnvironment.normalise(null)).isNull();
        assertThat(CloudEnvironment.normalise("  ")).isNull();
        assertThat(CloudEnvironment.normaliseToName("")).isNull();
    }
}
