package com.assetiq.services.impl;

import com.assetiq.dto.LeaseRecordDto;
import com.assetiq.models.Asset;
import com.assetiq.models.LeaseRecord;
import com.assetiq.models.Organisation;
import com.assetiq.models.Supplier;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.*;
import com.assetiq.services.CurrencyResolver;
import com.assetiq.services.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
@DisplayName("LeaseRecordServiceImpl - lessor is a supplier")
class LeaseRecordServiceImplTest {

    @Mock LeaseRecordRepository leaseRepository;
    @Mock AssetRepository assetRepository;
    @Mock SupplierRepository supplierRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock NotificationService notificationService;
    @Mock CurrencyResolver currencyResolver;

    private LeaseRecordServiceImpl service;
    private Organisation org;
    private Asset asset;
    private Supplier lessor;

    @BeforeEach
    void setUp() {
        service = new LeaseRecordServiceImpl(leaseRepository, assetRepository, supplierRepository,
                departmentRepository, organisationRepository, notificationService, currencyResolver);
        org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
        asset = new Asset();
        asset.setId(UUID.randomUUID());
        asset.setOrganisation(org);
        asset.setName("Forklift");
        lessor = new Supplier();
        lessor.setId(UUID.randomUUID());
        lessor.setName("LeaseCo");
        when(assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(asset.getId(), org)).thenReturn(Optional.of(asset));
        when(supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(lessor.getId(), org))
                .thenReturn(Optional.of(lessor));
        when(currencyResolver.resolveOrDefault(any())).thenReturn("GHS");
        when(leaseRepository.save(any(LeaseRecord.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("a lease with a supplier lessor is created and reports the lessor's name")
    void create_withSupplierLessor() {
        LeaseRecordDto result = service.create(request(lessor.getId()));
        assertThat(result.getLessorId()).isEqualTo(lessor.getId());
        assertThat(result.getLessorName()).isEqualTo("LeaseCo");
    }

    @Test
    @DisplayName("a lease without a lessor gets a clear 400 message")
    void create_requiresLessor() {
        assertThatThrownBy(() -> service.create(request(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lessor is required");
    }

    @Test
    @DisplayName("an end date before the start date is rejected")
    void create_rejectsInvertedPeriod() {
        LeaseRecordDto dto = request(lessor.getId());
        dto.setEndDate(dto.getStartDate().minusDays(1));
        assertThatThrownBy(() -> service.create(dto)).isInstanceOf(IllegalArgumentException.class);
    }

    private LeaseRecordDto request(UUID lessorId) {
        LeaseRecordDto dto = new LeaseRecordDto();
        dto.setAssetId(asset.getId());
        dto.setLessorId(lessorId);
        dto.setStartDate(LocalDate.of(2026, 1, 1));
        dto.setEndDate(LocalDate.of(2027, 1, 1));
        dto.setMonthlyPayment(new BigDecimal("250"));
        dto.setCurrency("GHS");
        return dto;
    }
}
