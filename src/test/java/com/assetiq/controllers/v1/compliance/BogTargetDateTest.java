package com.assetiq.controllers.v1.compliance;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BogTargetDateTest {

    @Test
    void acceptsTheWebFormsCalendarDate() {
        assertThat(BogComplianceController.parseTargetDate("2026-12-31"))
                .isEqualTo(Instant.parse("2026-12-31T12:00:00Z"));
    }

    @Test
    void acceptsAnInstant() {
        assertThat(BogComplianceController.parseTargetDate("2026-12-31T12:00:00Z"))
                .isEqualTo(Instant.parse("2026-12-31T12:00:00Z"));
    }

    @Test
    void blankClearsAndGarbageIsABadRequestNotSilentlyDropped() {
        assertThat(BogComplianceController.parseTargetDate("")).isNull();
        assertThat(BogComplianceController.parseTargetDate(null)).isNull();
        assertThatThrownBy(() -> BogComplianceController.parseTargetDate("31/12/2026"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
