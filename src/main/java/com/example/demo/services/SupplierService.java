package com.example.demo.services;

import com.example.demo.dto.SupplierDto;
import java.util.Set;
import java.util.UUID;

public interface SupplierService {
    SupplierDto createSupplier(SupplierDto supplierDto, UUID organisationId);
    SupplierDto getSupplierById(UUID id);
    Set<SupplierDto> getSuppliersByOrganisation(UUID organisationId);
    SupplierDto updateSupplier(UUID id, SupplierDto supplierDto);
    void deleteSupplier(UUID id);
    SupplierDto getSupplierByEmail(String email, UUID organisationId);
}

