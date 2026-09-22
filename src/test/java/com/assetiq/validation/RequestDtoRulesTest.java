package com.assetiq.validation;

import com.assetiq.dto.DepreciationPolicyDto;
import com.assetiq.dto.ExpenseDto;
import com.assetiq.dto.TenantRegisterRequest;
import com.assetiq.dto.UserDto;
import com.assetiq.enums.ExpenseCategory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.groups.Default;
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

    @Test
    void depreciationPolicy_createRequiresNameAndMethod() {
        assertThat(invalidFields(new DepreciationPolicyDto(), Default.class, OnCreate.class))
                .contains("name", "method");
    }

    @Test
    void depreciationPolicy_lifeAtLeastOneMonthAndResidualWithin0To100() {
        DepreciationPolicyDto dto = new DepreciationPolicyDto();
        dto.setUsefulLifeMonths(0);
        dto.setSalvageValuePercent(new BigDecimal("100.01"));

        assertThat(invalidFields(dto)).containsExactlyInAnyOrder("usefulLifeMonths", "salvageValuePercent");

        dto.setUsefulLifeMonths(null);
        dto.setSalvageValuePercent(new BigDecimal("-1"));
        assertThat(invalidFields(dto)).containsExactly("salvageValuePercent");
    }

    @Test
    void depreciationPolicy_blankLifeAndResidualAreAllowed() {
        DepreciationPolicyDto dto = new DepreciationPolicyDto();
        dto.setName("IT");
        dto.setMethod(com.assetiq.enums.DepreciationMethod.UNITS_OF_PRODUCTION);

        assertThat(invalidFields(dto, Default.class, OnCreate.class)).isEmpty();
    }

    @Test
    void password_policyIsEightCharactersTo72Bytes() {
        assertThat(PasswordPolicy.isValid("short")).isFalse();
        assertThat(PasswordPolicy.isValid("a".repeat(8))).isTrue();
        assertThat(PasswordPolicy.isValid("a".repeat(72))).isTrue();
        assertThat(PasswordPolicy.isValid("a".repeat(73))).isFalse();
        // BCrypt counts bytes: 24 three-byte characters are 72 bytes, 25 are too many.
        assertThat(PasswordPolicy.isValid("\u20AC".repeat(24))).isTrue();
        assertThat(PasswordPolicy.isValid("\u20AC".repeat(25))).isFalse();
    }

    @Test
    void password_everyEntryPointUsesThePolicy() {
        UserDto user = new UserDto();
        user.setPassword("a".repeat(73));
        assertThat(invalidFields(user)).contains("password");

        com.assetiq.dto.ChangePasswordRequest change = new com.assetiq.dto.ChangePasswordRequest();
        change.setCurrentPassword("anything");
        change.setNewPassword("a".repeat(73));
        assertThat(invalidFields(change)).containsExactly("newPassword");

        TenantRegisterRequest tenant = new TenantRegisterRequest();
        tenant.setPassword("a".repeat(100));
        assertThat(invalidFields(tenant)).contains("password");
    }
}
