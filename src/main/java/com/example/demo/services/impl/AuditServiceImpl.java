package com.example.demo.services.impl;

import com.example.demo.dto.AssetAuditDto;
import com.example.demo.models.AssetAudit;
import com.example.demo.models.Organisation;
import com.example.demo.models.Department;
import com.example.demo.models.User;
import com.example.demo.repositories.*;
import com.example.demo.services.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AuditServiceImpl implements AuditService {

    private final AssetAuditRepository auditRepository;
    private final OrganisationRepository organisationRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    public AuditServiceImpl(AssetAuditRepository auditRepository,
                          OrganisationRepository organisationRepository,
                          DepartmentRepository departmentRepository,
                          UserRepository userRepository) {
        this.auditRepository = auditRepository;
        this.organisationRepository = organisationRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
    }

    @Override
    public AssetAuditDto createAudit(AssetAuditDto auditDto) {
        Organisation organisation = organisationRepository.findById(auditDto.getOrganisationId())
            .orElseThrow(() -> new IllegalArgumentException("Organisation not found"));
        Department department = departmentRepository.findById(auditDto.getDepartmentId())
            .orElseThrow(() -> new IllegalArgumentException("Department not found"));
        User conductor = userRepository.findById(auditDto.getConductedById())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        AssetAudit audit = new AssetAudit();
        audit.setOrganisation(organisation);
        audit.setDepartment(department);
        audit.setAuditDate(auditDto.getAuditDate());
        audit.setConductedBy(conductor);
        audit.setStatus(auditDto.getStatus());
        audit.setRemarks(auditDto.getRemarks());

        AssetAudit savedAudit = auditRepository.save(audit);
        return mapToDto(savedAudit);
    }

    @Override
    @Transactional(readOnly = true)
    public AssetAuditDto getAuditById(UUID id) {
        AssetAudit audit = auditRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Audit not found"));
        return mapToDto(audit);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<AssetAuditDto> getAuditsByOrganisation(UUID organisationId) {
        return auditRepository.findByOrganisationId(organisationId).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<AssetAuditDto> getAuditsByDepartment(UUID departmentId) {
        return auditRepository.findByDepartmentId(departmentId).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<AssetAuditDto> getAuditsByDateRange(LocalDate startDate, LocalDate endDate) {
        return auditRepository.findByAuditDateBetween(startDate, endDate).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<AssetAuditDto> getAuditsByConductor(UUID userId) {
        return auditRepository.findByConductedById(userId).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    public AssetAuditDto updateAuditStatus(UUID auditId, String status) {
        AssetAudit audit = auditRepository.findById(auditId)
            .orElseThrow(() -> new IllegalArgumentException("Audit not found"));

        try {
            audit.setStatus(com.example.demo.enums.AuditStatus.valueOf(status));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid audit status");
        }

        AssetAudit updatedAudit = auditRepository.save(audit);
        return mapToDto(updatedAudit);
    }

    @Override
    public void deleteAudit(UUID id) {
        // Note: In production, audit deletion should be prevented entirely
        auditRepository.deleteById(id);
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

