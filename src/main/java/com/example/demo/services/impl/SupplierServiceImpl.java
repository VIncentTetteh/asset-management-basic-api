package com.example.demo.services.impl;

import com.example.demo.dto.SupplierDto;
import com.example.demo.models.Supplier;
import com.example.demo.models.Organisation;
import com.example.demo.repositories.SupplierRepository;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.services.SupplierService;
import com.example.demo.services.TenantAwareService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class SupplierServiceImpl extends TenantAwareService implements SupplierService {

    private final SupplierRepository supplierRepository;

    public SupplierServiceImpl(SupplierRepository supplierRepository,
            OrganisationRepository organisationRepository) {
        super(organisationRepository);
        this.supplierRepository = supplierRepository;
    }

    @Override
    public SupplierDto createSupplier(SupplierDto supplierDto, UUID organisationId) {
        // Always use tenant context, ignore param
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
        supplier.setStatus(supplierDto.getStatus());
        supplier.setOrganisation(org);

        return mapToDto(supplierRepository.save(supplier));
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
    public Set<SupplierDto> getSuppliersByOrganisation(UUID organisationId) {
        // Always scope to tenant context, ignore param
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
        supplier.setStatus(supplierDto.getStatus());

        return mapToDto(supplierRepository.save(supplier));
    }

    @Override
    public void deleteSupplier(UUID id) {
        Organisation org = requireTenantOrg();
        Supplier supplier = supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
        supplier.setDeletedAt(Instant.now());
        supplierRepository.save(supplier);
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierDto getSupplierByEmail(String email, UUID organisationId) {
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
        return dto;
    }
}
