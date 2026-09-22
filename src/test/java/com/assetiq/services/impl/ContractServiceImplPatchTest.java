package com.assetiq.services.impl;

import com.assetiq.dto.ContractDto;
import com.assetiq.enums.ContractType;
import com.assetiq.exceptions.FieldValidationException;
import com.assetiq.models.Contract;
import com.assetiq.models.Supplier;
import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.ContractRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.SupplierRepository;
import com.assetiq.services.CurrencyResolver;
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
class ContractServiceImplPatchTest {

    @Mock OrganisationRepository organisationRepository;
    @Mock ContractRepository contractRepository;
    @Mock SupplierRepository supplierRepository;
    @Mock AssetRepository assetRepository;
    @Mock CurrencyResolver currencyResolver;

    private ContractServiceImpl service;
    private Contract contract;
    private Organisation org;

    @BeforeEach
    void setUp() {
        service = new ContractServiceImpl(organisationRepository, contractRepository, supplierRepository,
                assetRepository, currencyResolver);
        org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
        contract = new Contract();
        contract.setId(UUID.randomUUID());
        contract.setOrganisation(org);
        contract.setTitle("Support");
        contract.setAutoRenew(true);
        when(contractRepository.findByIdAndOrganisationAndDeletedAtIsNull(contract.getId(), org))
                .thenReturn(Optional.of(contract));
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void unrelatedPatchKeepsAutoRenew() {
        ContractDto patch = new ContractDto();
        patch.setTitle("Support renewal");

        ContractDto saved = service.patch(contract.getId(), patch);

        assertThat(contract.isAutoRenew()).isTrue();
        assertThat(saved.getAutoRenew()).isTrue();
        assertThat(contract.getTitle()).isEqualTo("Support renewal");
    }

    @Test
    void explicitFalseSwitchesAutoRenewOff() {
        ContractDto patch = new ContractDto();
        patch.setAutoRenew(false);

        service.patch(contract.getId(), patch);

        assertThat(contract.isAutoRenew()).isFalse();
    }

    private ContractDto fullBody() {
        ContractDto dto = new ContractDto();
        dto.setTitle("Support");
        dto.setContractType(ContractType.MAINTENANCE);
        dto.setStartDate(LocalDate.of(2026, 1, 1));
        dto.setEndDate(LocalDate.of(2026, 12, 31));
        dto.setValue(new BigDecimal("100.00"));
        return dto;
    }

    @Test
    void putUnlinksSupplierAndClearsNotes() {
        Supplier supplier = new Supplier();
        supplier.setId(UUID.randomUUID());
        contract.setSupplier(supplier);
        contract.setNotes("Old terms");
        contract.setContractNumber("C-1");

        service.update(contract.getId(), fullBody());

        assertThat(contract.getSupplier()).isNull();
        assertThat(contract.getNotes()).isNull();
        assertThat(contract.getContractNumber()).isNull();
    }

    @Test
    void unknownSupplierIsRefusedNotIgnored() {
        ContractDto dto = fullBody();
        dto.setSupplierId(UUID.randomUUID());
        assertThatThrownBy(() -> service.update(contract.getId(), dto))
                .isInstanceOf(FieldValidationException.class)
                .extracting("field").isEqualTo("supplierId");
    }

    @Test
    void endBeforeStartIsRefusedOnCreatePutAndPatch() {
        ContractDto dto = fullBody();
        dto.setEndDate(LocalDate.of(2025, 12, 31));
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(FieldValidationException.class)
                .extracting("field").isEqualTo("endDate");
        assertThatThrownBy(() -> service.update(contract.getId(), dto))
                .isInstanceOf(FieldValidationException.class);

        contract.setStartDate(LocalDate.of(2026, 6, 1));
        contract.setEndDate(LocalDate.of(2026, 12, 31));
        ContractDto patch = new ContractDto();
        patch.setEndDate(LocalDate.of(2026, 5, 31));
        assertThatThrownBy(() -> service.patch(contract.getId(), patch))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void sameDayContractIsAllowed() {
        ContractDto dto = fullBody();
        dto.setEndDate(dto.getStartDate());
        assertThat(service.update(contract.getId(), dto).getEndDate()).isEqualTo(dto.getStartDate());
    }
}
