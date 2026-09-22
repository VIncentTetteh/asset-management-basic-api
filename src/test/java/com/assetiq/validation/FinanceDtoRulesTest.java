package com.assetiq.validation;

import com.assetiq.dto.ContractDto;
import com.assetiq.dto.LeaseRecordDto;
import com.assetiq.dto.PurchaseOrderRejectRequest;
import com.assetiq.dto.VendorPerformanceReviewDto;
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

    @Test
    void contract_valueAndAlertDaysAreNotNegative_documentUrlAtMost500() {
        ContractDto dto = new ContractDto();
        dto.setValue(new BigDecimal("-0.01"));
        dto.setAlertDaysBefore(-1);
        dto.setDocumentUrl("x".repeat(501));
        dto.setContractNumber("x".repeat(101));
        assertThat(invalidFields(dto)).containsExactlyInAnyOrder("value", "alertDaysBefore", "documentUrl", "contractNumber");
        dto.setValue(BigDecimal.ZERO);
        dto.setAlertDaysBefore(0);
        dto.setDocumentUrl("https://docs.example.com/c.pdf");
        dto.setContractNumber("C-1");
        assertThat(invalidFields(dto)).isEmpty();
    }

    @Test
    void lease_monthlyPaymentIsRequiredAndPositive_noticeNotNegative() {
        LeaseRecordDto dto = new LeaseRecordDto();
        dto.setAssetId(java.util.UUID.randomUUID());
        dto.setLessorId(java.util.UUID.randomUUID());
        dto.setStartDate(java.time.LocalDate.of(2026, 1, 1));
        dto.setEndDate(java.time.LocalDate.of(2027, 1, 1));
        dto.setNoticePeriodDays(-1);
        assertThat(invalidFields(dto)).containsExactlyInAnyOrder("monthlyPayment", "noticePeriodDays");
        dto.setMonthlyPayment(BigDecimal.ZERO);
        dto.setNoticePeriodDays(0);
        assertThat(invalidFields(dto)).containsExactly("monthlyPayment");
        dto.setMonthlyPayment(new BigDecimal("0.01"));
        assertThat(invalidFields(dto)).isEmpty();
    }

    @Test
    void vendorReview_periodIsRequired() {
        VendorPerformanceReviewDto dto = new VendorPerformanceReviewDto();
        dto.setRating(new BigDecimal("3.00"));
        assertThat(invalidFields(dto)).containsExactlyInAnyOrder("periodStart", "periodEnd");
        dto.setPeriodStart(java.time.LocalDate.of(2026, 1, 1));
        dto.setPeriodEnd(java.time.LocalDate.of(2026, 3, 31));
        assertThat(invalidFields(dto)).isEmpty();
    }
}
