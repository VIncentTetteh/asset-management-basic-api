package com.assetiq.validation;

import com.assetiq.dto.ExpenseDto;
import com.assetiq.enums.ExpenseCategory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Business rules on request DTOs that the web forms mirror in
 * {@code src/lib/field-limits.ts}: required fields, ranges and positive amounts.
 */
class RequestDtoRulesTest {

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
    void expense_requiresTitleAmountAndCategory() {
        assertThat(invalidFields(new ExpenseDto())).contains("title", "amount", "category");
    }

    @Test
    void expense_rejectsBlankTitleAndNonPositiveAmount() {
        ExpenseDto dto = new ExpenseDto();
        dto.setTitle("   ");
        dto.setAmount(BigDecimal.ZERO);
        dto.setCategory(ExpenseCategory.OTHER);

        assertThat(invalidFields(dto)).containsExactlyInAnyOrder("title", "amount");
    }

    @Test
    void expense_validSubmissionPasses() {
        ExpenseDto dto = new ExpenseDto();
        dto.setTitle("Fuel");
        dto.setAmount(new BigDecimal("0.01"));
        dto.setCategory(ExpenseCategory.OTHER);

        assertThat(invalidFields(dto)).isEmpty();
    }
}
