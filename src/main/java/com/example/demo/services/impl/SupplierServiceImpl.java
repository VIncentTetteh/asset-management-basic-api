package com.example.demo.services.impl;

import com.example.demo.dto.SupplierDto;
import com.example.demo.models.Supplier;
import com.example.demo.models.Organisation;
import com.example.demo.repositories.SupplierRepository;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.services.SupplierService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;
    private final OrganisationRepository organisationRepository;

    public SupplierServiceImpl(SupplierRepository supplierRepository, OrganisationRepository organisationRepository) {
        this.supplierRepository = supplierRepository;
        this.organisationRepository = organisationRepository;
    }

    @Override
    public SupplierDto createSupplier(SupplierDto supplierDto, UUID organisationId) {
        Organisation organisation = organisationRepository.findById(organisationId)
            .orElseThrow(() -> new IllegalArgumentException("Organisation not found"));

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
        supplier.setOrganisation(organisation);

        Supplier savedSupplier = supplierRepository.save(supplier);
        return mapToDto(savedSupplier);
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierDto getSupplierById(UUID id) {
        Supplier supplier = supplierRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
        return mapToDto(supplier);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<SupplierDto> getSuppliersByOrganisation(UUID organisationId) {
        return supplierRepository.findByOrganisationId(organisationId).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    public SupplierDto updateSupplier(UUID id, SupplierDto supplierDto) {
        Supplier supplier = supplierRepository.findById(id)
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

        Supplier updatedSupplier = supplierRepository.save(supplier);
        return mapToDto(updatedSupplier);
    }

    @Override
    public void deleteSupplier(UUID id) {
        supplierRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierDto getSupplierByEmail(String email, UUID organisationId) {
        Supplier supplier = supplierRepository.findByEmailAndOrganisationId(email, organisationId)
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

