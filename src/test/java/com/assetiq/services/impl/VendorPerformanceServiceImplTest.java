package com.assetiq.services.impl;

import com.assetiq.dto.VendorPerformanceReviewDto;
import com.assetiq.exceptions.FieldValidationException;
import com.assetiq.models.Organisation;
import com.assetiq.models.Supplier;
import com.assetiq.models.VendorPerformanceReview;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.SupplierRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.repositories.VendorPerformanceReviewRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VendorPerformanceServiceImplTest {

    @Mock OrganisationRepository organisationRepository;
    @Mock VendorPerformanceReviewRepository reviewRepository;
    @Mock SupplierRepository supplierRepository;
    @Mock UserRepository userRepository;

    private VendorPerformanceServiceImpl service;
    private Organisation org;
    private Supplier supplier;
    private VendorPerformanceReview review;

    @BeforeEach
    void setUp() {
        service = new VendorPerformanceServiceImpl(organisationRepository, reviewRepository, supplierRepository,
                userRepository);
        org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
        supplier = new Supplier();
        supplier.setId(UUID.randomUUID());
        supplier.setName("Vendor");
        when(supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(supplier.getId(), org))
                .thenReturn(Optional.of(supplier));
        review = new VendorPerformanceReview();
        review.setId(UUID.randomUUID());
        review.setOrganisation(org);
        review.setSupplier(supplier);
        review.setRating(new BigDecimal("4.00"));
        review.setFeedback("Late twice");
        when(reviewRepository.findByIdAndOrganisationAndDeletedAtIsNull(review.getId(), org))
                .thenReturn(Optional.of(review));
        when(reviewRepository.save(any(VendorPerformanceReview.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private VendorPerformanceReviewDto body(LocalDate start, LocalDate end) {
        VendorPerformanceReviewDto dto = new VendorPerformanceReviewDto();
        dto.setSupplierId(supplier.getId());
        dto.setRating(new BigDecimal("3.00"));
        dto.setPeriodStart(start);
        dto.setPeriodEnd(end);
        return dto;
    }

    @Test
    void periodEndBeforeStartIsRefusedOnCreateAndUpdate() {
        VendorPerformanceReviewDto dto = body(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 5, 31));
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(FieldValidationException.class)
                .extracting("field").isEqualTo("periodEnd");
        assertThatThrownBy(() -> service.update(review.getId(), dto))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void updateClearsFeedbackWithNull() {
        service.update(review.getId(), body(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)));
        assertThat(review.getFeedback()).isNull();
        assertThat(review.getRating()).isEqualByComparingTo("3.00");
        assertThat(review.getSupplier()).isSameAs(supplier);
    }

    @Test
    void createWithoutSupplierIsAFieldError() {
        VendorPerformanceReviewDto dto = body(null, null);
        dto.setSupplierId(null);
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(FieldValidationException.class)
                .extracting("field").isEqualTo("supplierId");
    }
}
