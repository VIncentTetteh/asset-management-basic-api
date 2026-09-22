package com.assetiq.validation;

import com.assetiq.dto.PurchaseOrderRejectRequest;
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
 * Finance and procurement request rules that the web forms mirror in
 * {@code src/lib/field-limits.ts}.
 */
class FinanceDtoRulesTest {

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

    static Set<String> invalidFields(Object dto, Class<?>... groups) {
        Set<? extends ConstraintViolation<?>> violations = validator.validate(dto, groups);
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void purchaseOrderReject_requiresAReasonOfAtMost5000() {
        assertThat(invalidFields(new PurchaseOrderRejectRequest(" "))).containsExactly("reason");
        assertThat(invalidFields(new PurchaseOrderRejectRequest("x".repeat(5001)))).containsExactly("reason");
        assertThat(invalidFields(new PurchaseOrderRejectRequest("Over budget"))).isEmpty();
    }
}
