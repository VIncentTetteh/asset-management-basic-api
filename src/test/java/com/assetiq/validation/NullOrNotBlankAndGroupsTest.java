package com.assetiq.validation;

import com.assetiq.dto.AssetDto;
import com.assetiq.dto.compliance.RegulatoryFilingDto;
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
 * A PATCH ({@code @Valid}, default group) may omit a required field but may not
 * blank it or exceed its column; a create/full replace ({@code OnCreate}) must
 * still carry it.
 */
class NullOrNotBlankAndGroupsTest {

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

    private static Set<String> fields(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void patch_absentRequiredFieldIsAllowed() {
        AssetDto patch = new AssetDto();

        assertThat(validator.validate(patch)).isEmpty();
    }

    @Test
    void patch_blankRequiredFieldIsRejected() {
        AssetDto patch = new AssetDto();
        patch.setName("   ");

        assertThat(fields(validator.validate(patch))).containsExactly("name");
    }

    @Test
    void patch_stillEnforcesColumnLimits() {
        RegulatoryFilingDto patch = new RegulatoryFilingDto();
        patch.setRegulator("Securities and Exchange Commission Ghana");

        assertThat(fields(validator.validate(patch))).containsExactly("regulator");
    }

    @Test
    void create_requiresTheField() {
        AssetDto create = new AssetDto();

        assertThat(fields(validator.validate(create, OnCreate.class))).containsExactly("name");
    }

    @Test
    void create_alsoRunsDefaultGroupConstraints() {
        AssetDto create = new AssetDto();
        create.setName("Laptop");
        create.setCostCenter("x".repeat(101));

        assertThat(fields(validator.validate(create, OnCreate.class))).containsExactly("costCenter");
    }

    @Test
    void nullOrNotBlankValidator_acceptsNullAndText_rejectsWhitespace() {
        NullOrNotBlankValidator v = new NullOrNotBlankValidator();

        assertThat(v.isValid(null, null)).isTrue();
        assertThat(v.isValid("a", null)).isTrue();
        assertThat(v.isValid("", null)).isFalse();
        assertThat(v.isValid(" \t", null)).isFalse();
    }
}
