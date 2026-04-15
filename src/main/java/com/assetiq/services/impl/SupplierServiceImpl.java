package com.assetiq.services.impl;

import com.assetiq.dto.SupplierDto;
import com.assetiq.models.Supplier;
import com.assetiq.models.Organisation;
import com.assetiq.enums.SupplierStatus;
import com.assetiq.repositories.SupplierRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.SupplierService;
import com.assetiq.services.TenantAwareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class SupplierServiceImpl extends TenantAwareService implements SupplierService {

    private static final Logger logger = LoggerFactory.getLogger(SupplierServiceImpl.class);

    private final SupplierRepository supplierRepository;

    public SupplierServiceImpl(SupplierRepository supplierRepository,
            OrganisationRepository organisationRepository) {
        super(organisationRepository);
        this.supplierRepository = supplierRepository;
    }

    @Override
    public SupplierDto createSupplier(SupplierDto supplierDto) {
        Organisation org = requireTenantOrg();

        Supplier supplier = new Supplier();
        supplier.setName(supplierDto.getName());
        supplier.setRegistrationNumber(supplierDto.getRegistrationNumber());
        supplier.setContactPerson(supplierDto.getContactPerson());
        supplier.setEmail(supplierDto.getEmail());
        supplier.setPhone(supplierDto.getPhone());
        supplier.setAddress(supplierDto.getAddress());
        supplier.setBankDetails(supplierDto.getBankDetails());
        supplier.setTaxId(supplierDto.getTaxId());
        supplier.setStatus(supplierDto.getStatus() != null ? supplierDto.getStatus() : SupplierStatus.ACTIVE);
        supplier.setOrganisation(org);

        Supplier saved = supplierRepository.save(supplier);
        logger.info("Created Supplier {} (Name: {})", saved.getId(), saved.getName());
        return mapToDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierDto getSupplierById(UUID id) {
        Organisation org = requireTenantOrg();
        Supplier supplier = supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
        return mapToDto(supplier);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<SupplierDto> getSuppliersByOrganisation() {
        Organisation org = requireTenantOrg();
        return supplierRepository.findByOrganisationAndDeletedAtIsNull(org).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    public SupplierDto updateSupplier(UUID id, SupplierDto supplierDto) {
        Organisation org = requireTenantOrg();
        Supplier supplier = supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found"));

        supplier.setName(supplierDto.getName());
        supplier.setRegistrationNumber(supplierDto.getRegistrationNumber());
        supplier.setContactPerson(supplierDto.getContactPerson());
        supplier.setEmail(supplierDto.getEmail());
        supplier.setPhone(supplierDto.getPhone());
        supplier.setAddress(supplierDto.getAddress());
        supplier.setBankDetails(supplierDto.getBankDetails());
        supplier.setTaxId(supplierDto.getTaxId());
        supplier.setStatus(supplierDto.getStatus() != null ? supplierDto.getStatus() : supplier.getStatus());

        Supplier saved = supplierRepository.save(supplier);
        logger.info("Updated Supplier {} (Name: {})", id, saved.getName());
        return mapToDto(saved);
    }

    @Override
    public SupplierDto patchSupplier(UUID id, SupplierDto supplierDto) {
        Organisation org = requireTenantOrg();
        Supplier supplier = supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found"));

        if (supplierDto.getName() != null) {
            supplier.setName(supplierDto.getName());
        }
        if (supplierDto.getRegistrationNumber() != null) {
            supplier.setRegistrationNumber(supplierDto.getRegistrationNumber());
        }
        if (supplierDto.getContactPerson() != null) {
            supplier.setContactPerson(supplierDto.getContactPerson());
        }
        if (supplierDto.getEmail() != null) {
            supplier.setEmail(supplierDto.getEmail());
        }
        if (supplierDto.getPhone() != null) {
            supplier.setPhone(supplierDto.getPhone());
        }
        if (supplierDto.getAddress() != null) {
            supplier.setAddress(supplierDto.getAddress());
        }
        if (supplierDto.getBankDetails() != null) {
            supplier.setBankDetails(supplierDto.getBankDetails());
        }
        if (supplierDto.getTaxId() != null) {
            supplier.setTaxId(supplierDto.getTaxId());
        }
        if (supplierDto.getStatus() != null) {
            supplier.setStatus(supplierDto.getStatus());
        }

        Supplier saved = supplierRepository.save(supplier);
        logger.info("Patched Supplier {} (Name: {})", id, saved.getName());
        return mapToDto(saved);
    }

    @Override
    public void deleteSupplier(UUID id) {
        Organisation org = requireTenantOrg();
        Supplier supplier = supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
        supplier.setDeletedAt(Instant.now());
        supplierRepository.save(supplier);
        logger.info("Soft-deleted Supplier {} (Name: {})", id, supplier.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierDto getSupplierByEmail(String email) {
        Organisation org = requireTenantOrg();
        Supplier supplier = supplierRepository.findByEmailAndOrganisationAndDeletedAtIsNull(email, org)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
        return mapToDto(supplier);
    }

    private SupplierDto mapToDto(Supplier supplier) {
        SupplierDto dto = new SupplierDto();
        dto.setId(supplier.getId());
        dto.setName(supplier.getName());
        dto.setRegistrationNumber(supplier.getRegistrationNumber());
        dto.setContactPerson(supplier.getContactPerson());
        dto.setEmail(supplier.getEmail());
        dto.setPhone(supplier.getPhone());
        dto.setAddress(supplier.getAddress());
        dto.setBankDetails(supplier.getBankDetails());
        dto.setTaxId(supplier.getTaxId());
        dto.setStatus(supplier.getStatus());
        dto.setOrganisationId(supplier.getOrganisation().getId());
        dto.setCreatedAt(supplier.getCreatedAt());
        dto.setUpdatedAt(supplier.getUpdatedAt());
        return dto;
    }
}
