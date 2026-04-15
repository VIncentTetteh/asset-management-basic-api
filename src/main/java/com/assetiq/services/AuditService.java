package com.assetiq.services;

import com.assetiq.dto.AssetAuditDto;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public interface AuditService {
    AssetAuditDto createAudit(AssetAuditDto auditDto);
    AssetAuditDto getAuditById(UUID id);
    Set<AssetAuditDto> getAuditsByOrganisation(UUID organisationId);
    Set<AssetAuditDto> getAuditsByDepartment(UUID departmentId);
    Set<AssetAuditDto> getAuditsByDateRange(LocalDate startDate, LocalDate endDate);
    Set<AssetAuditDto> getAuditsByConductor(UUID userId);
    AssetAuditDto updateAuditStatus(UUID auditId, String status);
    void deleteAudit(UUID id); // Note: deletion should be prevented for immutable audits in production
}

