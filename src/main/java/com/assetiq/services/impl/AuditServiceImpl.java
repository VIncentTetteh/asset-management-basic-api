package com.assetiq.services.impl;

import com.assetiq.dto.AssetAuditDto;
import com.assetiq.models.AssetAudit;
import com.assetiq.models.Organisation;
import com.assetiq.models.Department;
import com.assetiq.models.User;
import com.assetiq.repositories.*;
import com.assetiq.services.AuditService;
import com.assetiq.services.TenantAwareService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AuditServiceImpl extends TenantAwareService implements AuditService {

    private final AssetAuditRepository auditRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    public AuditServiceImpl(AssetAuditRepository auditRepository,
            OrganisationRepository organisationRepository,
            DepartmentRepository departmentRepository,
            UserRepository userRepository) {
        super(organisationRepository);
        this.auditRepository = auditRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
    }

    @Override
    public AssetAuditDto createAudit(AssetAuditDto auditDto) {
        Organisation org = requireTenantOrg();

        Department department = departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(
                auditDto.getDepartmentId(), org)
                .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));

        User conductor = userRepository.findByIdAndOrganisation(auditDto.getConductedById(), org)
                .orElseThrow(() -> new IllegalArgumentException("Conductor not found in your organisation"));

        AssetAudit audit = new AssetAudit();
        audit.setOrganisation(org);
        audit.setDepartment(department);
        audit.setAuditDate(auditDto.getAuditDate());
        audit.setConductedBy(conductor);
        audit.setStatus(auditDto.getStatus());
        audit.setRemarks(auditDto.getRemarks());

        return mapToDto(auditRepository.save(audit));
    }

    @Override
    @Transactional(readOnly = true)
    public AssetAuditDto getAuditById(UUID id) {
        Organisation org = requireTenantOrg();
        AssetAudit audit = auditRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Audit not found"));
        return mapToDto(audit);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<AssetAuditDto> getAuditsByOrganisation(UUID organisationId) {
        // Always scope to tenant context, ignore param
        Organisation org = requireTenantOrg();
        return auditRepository.findByOrganisationAndDeletedAtIsNull(org).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<AssetAuditDto> getAuditsByDepartment(UUID departmentId) {
        Organisation org = requireTenantOrg();
        departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(departmentId, org)
                .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));
        return auditRepository.findByDepartmentIdAndDeletedAtIsNull(departmentId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<AssetAuditDto> getAuditsByDateRange(LocalDate startDate, LocalDate endDate) {
        Organisation org = requireTenantOrg();
        return auditRepository.findByOrganisationAndAuditDateBetweenAndDeletedAtIsNull(org, startDate, endDate)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<AssetAuditDto> getAuditsByConductor(UUID userId) {
        Organisation org = requireTenantOrg();
        userRepository.findByIdAndOrganisation(userId, org)
                .orElseThrow(() -> new IllegalArgumentException("User not found in your organisation"));
        return auditRepository.findByConductedByIdAndDeletedAtIsNull(userId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    public AssetAuditDto updateAuditStatus(UUID auditId, String status) {
        Organisation org = requireTenantOrg();
        AssetAudit audit = auditRepository.findByIdAndOrganisationAndDeletedAtIsNull(auditId, org)
                .orElseThrow(() -> new IllegalArgumentException("Audit not found"));

        try {
            audit.setStatus(com.assetiq.enums.AuditStatus.valueOf(status));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid audit status: " + status);
        }

        return mapToDto(auditRepository.save(audit));
    }

    @Override
    public void deleteAudit(UUID id) {
        // Audit records should not be deleted — they are immutable compliance records
        throw new IllegalStateException("Audit records cannot be deleted. They are immutable compliance records.");
    }

    private AssetAuditDto mapToDto(AssetAudit audit) {
        AssetAuditDto dto = new AssetAuditDto();
        dto.setId(audit.getId());
        dto.setOrganisationId(audit.getOrganisation().getId());
        dto.setDepartmentId(audit.getDepartment().getId());
        dto.setAuditDate(audit.getAuditDate());
        dto.setConductedById(audit.getConductedBy().getId());
        dto.setStatus(audit.getStatus());
        dto.setRemarks(audit.getRemarks());
        return dto;
    }
}
