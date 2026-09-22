package com.assetiq.validation;

import com.assetiq.dto.LocationDto;
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
}
