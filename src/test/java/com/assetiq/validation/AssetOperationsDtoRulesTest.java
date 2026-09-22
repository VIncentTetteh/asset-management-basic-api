package com.assetiq.validation;

import com.assetiq.dto.DisposalRecordDto;
import com.assetiq.dto.DisposalRejectRequest;
import com.assetiq.dto.LocationDto;
import com.assetiq.enums.DisposalMethod;
import java.time.LocalDate;
import com.assetiq.dto.MaintenanceRecordDto;
import com.assetiq.enums.MaintenanceType;
import jakarta.validation.groups.Default;
import java.math.BigDecimal;
import java.util.UUID;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Request rules for the assets and operations screens (locations, departments,
 * maintenance, disposals, audits, cloud) that the web forms mirror in
 * {@code src/lib/field-limits.ts}.
 */
class AssetOperationsDtoRulesTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static Set<String> invalidFields(Object dto, Class<?>... groups) {
        Set<? extends ConstraintViolation<?>> violations = validator.validate(dto, groups);
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void locationCountryIsAnIsoCodeOrNothing() {
        LocationDto dto = new LocationDto();
        dto.setName("HQ");
        assertThat(invalidFields(dto)).isEmpty();

        dto.setCountry("GH");
        assertThat(invalidFields(dto)).isEmpty();

        dto.setCountry("Ghana");
        assertThat(invalidFields(dto)).containsExactly("country");

        dto.setCountry("gh");
        assertThat(invalidFields(dto)).containsExactly("country");
    }

    @Test
    void maintenanceNeedsAScheduledDateOnCreateAndACostOfZeroOrMore() {
        MaintenanceRecordDto dto = new MaintenanceRecordDto();
        dto.setAssetId(UUID.randomUUID());
        dto.setMaintenanceType(MaintenanceType.ROUTINE);
        dto.setCost(new BigDecimal("-1"));

        assertThat(invalidFields(dto, Default.class, OnCreate.class)).containsExactlyInAnyOrder("scheduledDate", "cost");
        assertThat(invalidFields(dto)).containsExactly("cost");
    }

    @Test
    void disposalNeedsAReasonOnCreateAndRejectionNeedsOne() {
        DisposalRecordDto dto = new DisposalRecordDto();
        dto.setAssetId(UUID.randomUUID());
        dto.setDisposalMethod(DisposalMethod.SALE);
        dto.setDisposalDate(LocalDate.of(2026, 9, 1));
        assertThat(invalidFields(dto, Default.class, OnCreate.class)).containsExactly("reason");
        dto.setReason("x".repeat(5001));
        assertThat(invalidFields(dto)).containsExactly("reason");

        assertThat(invalidFields(new DisposalRejectRequest(" "))).containsExactly("reason");
        assertThat(invalidFields(new DisposalRejectRequest("Still under warranty"))).isEmpty();
    }
}
