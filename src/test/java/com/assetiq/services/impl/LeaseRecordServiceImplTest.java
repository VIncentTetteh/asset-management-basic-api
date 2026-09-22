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

    private LeaseRecord existing() {
        LeaseRecord record = new LeaseRecord();
        record.setId(UUID.randomUUID());
        record.setOrganisation(org);
        record.setAsset(asset);
        record.setLessor(lessor);
        record.setStartDate(LocalDate.of(2026, 1, 1));
        record.setEndDate(LocalDate.of(2027, 1, 1));
        record.setMonthlyPayment(new BigDecimal("250"));
        record.setStatus(com.assetiq.enums.LeaseStatus.TERMINATED);
        record.setNotes("Termination reason: moved office");
        record.setNoticePeriodDays(0);
        when(leaseRepository.findByIdAndOrganisationAndDeletedAtIsNull(record.getId(), org))
                .thenReturn(Optional.of(record));
        return record;
    }

    @Test
    @DisplayName("PUT moves the lease to another asset, clears notes and department, never changes status")
    void update_fullReplace() {
        LeaseRecord record = existing();
        com.assetiq.models.Department dept = new com.assetiq.models.Department();
        dept.setId(UUID.randomUUID());
        record.setDepartment(dept);
        Asset other = new Asset();
        other.setId(UUID.randomUUID());
        other.setOrganisation(org);
        other.setName("Crane");
        when(assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(other.getId(), org)).thenReturn(Optional.of(other));

        LeaseRecordDto dto = request(lessor.getId());
        dto.setAssetId(other.getId());
        dto.setStatus(com.assetiq.enums.LeaseStatus.ACTIVE);
        LeaseRecordDto result = service.update(record.getId(), dto);

        assertThat(result.getAssetName()).isEqualTo("Crane");
        assertThat(record.getNotes()).isNull();
        assertThat(record.getDepartment()).isNull();
        assertThat(record.getStatus()).isEqualTo(com.assetiq.enums.LeaseStatus.TERMINATED);
        assertThat(record.getNoticePeriodDays()).isZero();
    }

    @Test
    @DisplayName("PUT refuses an unknown asset or department as field errors")
    void update_unknownLinks() {
        LeaseRecord record = existing();
        LeaseRecordDto dto = request(lessor.getId());
        dto.setAssetId(UUID.randomUUID());
        assertThatThrownBy(() -> service.update(record.getId(), dto))
                .isInstanceOf(com.assetiq.exceptions.FieldValidationException.class)
                .extracting("field").isEqualTo("assetId");

        dto.setAssetId(asset.getId());
        dto.setDepartmentId(UUID.randomUUID());
        assertThatThrownBy(() -> service.update(record.getId(), dto))
                .isInstanceOf(com.assetiq.exceptions.FieldValidationException.class)
                .extracting("field").isEqualTo("departmentId");
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
