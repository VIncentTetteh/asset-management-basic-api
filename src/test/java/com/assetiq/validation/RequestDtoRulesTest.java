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
    void phone_isOneRuleEverywhereItAppears() {
        // Phone fields only had a length limit, so "n/a" and a pasted paragraph
        // were both stored. The web app's FIELD_LIMITS phone entries use the
        // same regex as ValidPhone.PATTERN.
        for (String ok : new String[] {"+233 20 123 4567", "+233200000123", "020-123-4567", "(030) 276 1000", null, ""}) {
            UserDto user = new UserDto();
            user.setPhone(ok);
            assertThat(invalidFields(user)).as("accepts %s", ok).doesNotContain("phone");
        }
        for (String bad : new String[] {"n/a", "call me", "12345", "0800 CALL NOW"}) {
            UserDto user = new UserDto();
            user.setPhone(bad);
            assertThat(invalidFields(user)).as("rejects %s", bad).contains("phone");
        }

        TenantRegisterRequest register = new TenantRegisterRequest();
        register.setContactPhone("not a phone");
        register.setAdminPhone("also not");
        assertThat(invalidFields(register)).contains("contactPhone", "adminPhone");
    }

    @Test
    void networkScan_checksTheRangeAndTheAddressesItIsGiven() {
        com.assetiq.dto.NetworkScanRequestDto ok = new com.assetiq.dto.NetworkScanRequestDto();
        ok.setCidrRange("192.168.1.0/24");
        ok.setIpAddresses(java.util.List.of("192.168.1.10", "10.0.0.5"));
        ok.setPorts(java.util.List.of(22, 443));
        assertThat(invalidFields(ok)).isEmpty();

        // The shape used to be checked in the service only, so a typo came back
        // as a bare 400 rather than a field error the form could mark.
        com.assetiq.dto.NetworkScanRequestDto bad = new com.assetiq.dto.NetworkScanRequestDto();
        bad.setCidrRange("example.com/24");
        bad.setIpAddresses(java.util.List.of("not-an-ip"));
        bad.setPorts(java.util.List.of(70000));
        bad.setTimeoutMs(60_000);
        assertThat(invalidFields(bad))
                .contains("cidrRange", "timeoutMs")
                .anyMatch(f -> f.startsWith("ipAddresses"))
                .anyMatch(f -> f.startsWith("ports"));
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
