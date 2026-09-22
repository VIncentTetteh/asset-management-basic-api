package com.assetiq.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeZoneIdValidatorTest {

    private final TimeZoneIdValidator validator = new TimeZoneIdValidator();

    @Test
    void regionIdsAndBlankAreValid() {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid(" ", null)).isTrue();
        assertThat(validator.isValid("Africa/Accra", null)).isTrue();
        assertThat(validator.isValid("Europe/London", null)).isTrue();
        assertThat(validator.isValid("UTC", null)).isTrue();
    }

    @Test
    void typosAndOffsetsAreRefused() {
        assertThat(validator.isValid("Africa/Acra", null)).isFalse();
        assertThat(validator.isValid("+02:00", null)).isFalse();
        assertThat(validator.isValid("EST", null)).isFalse();
    }
}
