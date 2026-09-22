package com.assetiq.services.impl;

import com.assetiq.dto.ContractDto;
import com.assetiq.models.Contract;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    @BeforeEach
    void setUp() {
        service = new ContractServiceImpl(organisationRepository, contractRepository, supplierRepository,
                assetRepository, currencyResolver);
        Organisation org = new Organisation();
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
}
