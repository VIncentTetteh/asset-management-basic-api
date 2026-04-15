package com.assetiq.services.impl;

import com.assetiq.dto.ContractDto;
import com.assetiq.enums.ContractStatus;
import com.assetiq.models.Contract;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.*;
import com.assetiq.services.ContractService;
import com.assetiq.services.TenantAwareService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ContractServiceImpl extends TenantAwareService implements ContractService {

    private final ContractRepository contractRepository;
    private final SupplierRepository supplierRepository;
    private final AssetRepository assetRepository;

    public ContractServiceImpl(OrganisationRepository organisationRepository,
                               ContractRepository contractRepository,
                               SupplierRepository supplierRepository,
                               AssetRepository assetRepository) {
        super(organisationRepository);
        this.contractRepository = contractRepository;
        this.supplierRepository = supplierRepository;
        this.assetRepository = assetRepository;
    }

    @Override
    @Transactional
    public ContractDto create(ContractDto dto) {
        Organisation org = requireTenantOrg();
        Contract contract = new Contract();
        applyFields(contract, dto, org);
        return toDto(contractRepository.save(contract));
    }

    @Override
    public ContractDto getById(UUID id) {
        Organisation org = requireTenantOrg();
        return toDto(contractRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found: " + id)));
    }

    @Override
    public List<ContractDto> listAll() {
        Organisation org = requireTenantOrg();
        return contractRepository.findByOrganisationAndDeletedAtIsNullOrderByEndDateAsc(org)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public List<ContractDto> listExpiringSoon(int days) {
        Organisation org = requireTenantOrg();
        LocalDate cutoff = LocalDate.now().plusDays(days);
        return contractRepository.findExpiringSoon(org, cutoff)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ContractDto update(UUID id, ContractDto dto) {
        Organisation org = requireTenantOrg();
        Contract contract = contractRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found: " + id));
        applyFields(contract, dto, org);
        return toDto(contractRepository.save(contract));
    }

    @Override
    @Transactional
    public ContractDto patch(UUID id, ContractDto dto) {
        Organisation org = requireTenantOrg();
        Contract contract = contractRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found: " + id));

        if (dto.getTitle() != null) contract.setTitle(dto.getTitle());
        if (dto.getContractNumber() != null) contract.setContractNumber(dto.getContractNumber());
        if (dto.getContractType() != null) contract.setContractType(dto.getContractType());
        if (dto.getStatus() != null) contract.setStatus(dto.getStatus());
        if (dto.getStartDate() != null) contract.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null) contract.setEndDate(dto.getEndDate());
        if (dto.getAlertDaysBefore() != null) contract.setAlertDaysBefore(dto.getAlertDaysBefore());
        if (dto.getValue() != null) contract.setValue(dto.getValue());
        if (dto.getCurrency() != null) contract.setCurrency(dto.getCurrency());
        if (dto.getDocumentUrl() != null) contract.setDocumentUrl(dto.getDocumentUrl());
        if (dto.getNotes() != null) contract.setNotes(dto.getNotes());
        contract.setAutoRenew(dto.isAutoRenew());

        if (dto.getSupplierId() != null) {
            supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getSupplierId(), org)
                    .ifPresent(contract::setSupplier);
        }
        if (dto.getAssetId() != null) {
            assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getAssetId(), org)
                    .ifPresent(contract::setAsset);
        }

        return toDto(contractRepository.save(contract));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Organisation org = requireTenantOrg();
        Contract contract = contractRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found: " + id));
        contract.setDeletedAt(Instant.now());
        contractRepository.save(contract);
    }

    private void applyFields(Contract contract, ContractDto dto, Organisation org) {
        contract.setTitle(dto.getTitle());
        contract.setContractNumber(dto.getContractNumber());
        contract.setContractType(dto.getContractType());
        contract.setStatus(dto.getStatus() != null ? dto.getStatus() : ContractStatus.DRAFT);
        contract.setStartDate(dto.getStartDate());
        contract.setEndDate(dto.getEndDate());
        contract.setAlertDaysBefore(dto.getAlertDaysBefore() != null ? dto.getAlertDaysBefore() : 30);
        contract.setValue(dto.getValue());
        contract.setCurrency(dto.getCurrency() != null ? dto.getCurrency() : "USD");
        contract.setAutoRenew(dto.isAutoRenew());
        contract.setDocumentUrl(dto.getDocumentUrl());
        contract.setNotes(dto.getNotes());
        contract.setOrganisation(org);

        if (dto.getSupplierId() != null) {
            supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getSupplierId(), org)
                    .ifPresent(contract::setSupplier);
        }
        if (dto.getAssetId() != null) {
            assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getAssetId(), org)
                    .ifPresent(contract::setAsset);
        }
    }

    private ContractDto toDto(Contract c) {
        ContractDto d = new ContractDto();
        d.setId(c.getId());
        d.setTitle(c.getTitle());
        d.setContractNumber(c.getContractNumber());
        d.setContractType(c.getContractType());
        d.setStatus(c.getStatus());
        d.setStartDate(c.getStartDate());
        d.setEndDate(c.getEndDate());
        d.setAlertDaysBefore(c.getAlertDaysBefore());
        d.setValue(c.getValue());
        d.setCurrency(c.getCurrency());
        d.setAutoRenew(c.isAutoRenew());
        d.setDocumentUrl(c.getDocumentUrl());
        d.setNotes(c.getNotes());
        if (c.getEndDate() != null) {
            d.setDaysUntilExpiry(ChronoUnit.DAYS.between(LocalDate.now(), c.getEndDate()));
        }
        if (c.getSupplier() != null) {
            d.setSupplierId(c.getSupplier().getId());
            d.setSupplierName(c.getSupplier().getName());
        }
        if (c.getAsset() != null) {
            d.setAssetId(c.getAsset().getId());
            d.setAssetName(c.getAsset().getName());
        }
        return d;
    }
}
