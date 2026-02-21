package com.example.demo.services.impl;

import com.example.demo.dto.DepartmentDto;
import com.example.demo.models.Department;
import com.example.demo.models.Organisation;
import com.example.demo.multitenancy.TenantContext;
import com.example.demo.repositories.DepartmentRepository;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.services.DepartmentService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final OrganisationRepository organisationRepository;

    public DepartmentServiceImpl(DepartmentRepository departmentRepository, OrganisationRepository organisationRepository) {
        this.departmentRepository = departmentRepository;
        this.organisationRepository = organisationRepository;
    }

    @Override
    public DepartmentDto create(DepartmentDto dto) {

        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Department name is required");
        }

        String name = dto.getName().trim();

        Organisation organisation = null;
        if (TenantContext.hasOrganisationId()) {
            UUID orgId = TenantContext.getOrganisationId();
            organisation = organisationRepository.findByIdAndDeletedAtIsNull(orgId)
                    .orElseThrow(() -> new IllegalArgumentException("Organisation not found for tenant"));

            if (departmentRepository.existsByNameIgnoreCaseAndOrganisationAndDeletedAtIsNull(name, organisation)) {
                throw new IllegalStateException("Department with the same name already exists in this organisation");
            }
        } else {
            if (departmentRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(name)) {
                throw new IllegalStateException("Department with the same name already exists");
            }
            if (dto.getOrganisationId() != null) {
                organisation = organisationRepository
                        .findByIdAndDeletedAtIsNull(dto.getOrganisationId())
                        .orElse(null);
            }
        }

        Department department = new Department();
        department.setName(name);
        if (organisation != null) department.setOrganisation(organisation);

        Department saved = departmentRepository.save(department);
        return toDto(saved);
    }


    @Override
    public DepartmentDto get(UUID id) {
        if (TenantContext.hasOrganisationId()) {
            UUID orgId = TenantContext.getOrganisationId();
            Organisation org = organisationRepository.findByIdAndDeletedAtIsNull(orgId).orElseThrow(() -> new IllegalArgumentException("Organisation not found for tenant"));
            return departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org).map(this::toDto).orElse(null);
        }
        return departmentRepository.findByIdAndDeletedAtIsNull(id).map(this::toDto).orElse(null);
    }

    @Override
    public List<DepartmentDto> list() {
        if (TenantContext.hasOrganisationId()) {
            UUID orgId = TenantContext.getOrganisationId();
            Organisation org = organisationRepository.findByIdAndDeletedAtIsNull(orgId).orElseThrow(() -> new IllegalArgumentException("Organisation not found for tenant"));
            return departmentRepository.findAllByOrganisationAndDeletedAtIsNull(org).stream().map(this::toDto).collect(Collectors.toList());
        }
        return departmentRepository.findAllByDeletedAtIsNull().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public DepartmentDto update(UUID id, DepartmentDto dto) {
        Department d;
        if (TenantContext.hasOrganisationId()) {
            UUID orgId = TenantContext.getOrganisationId();
            Organisation org = organisationRepository.findByIdAndDeletedAtIsNull(orgId).orElseThrow(() -> new IllegalArgumentException("Organisation not found for tenant"));
            d = departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org).orElseThrow(() -> new IllegalArgumentException("Department not found"));
        } else {
            d = departmentRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new IllegalArgumentException("Department not found"));
        }

        if (dto.getName() != null) d.setName(dto.getName());
        if (dto.getOrganisationId() != null && !TenantContext.hasOrganisationId()) organisationRepository.findByIdAndDeletedAtIsNull(dto.getOrganisationId()).ifPresent(d::setOrganisation);
        Department saved = departmentRepository.save(d);
        return toDto(saved);
    }

    @Override
    public void delete(UUID id) {
        Department d;
        if (TenantContext.hasOrganisationId()) {
            UUID orgId = TenantContext.getOrganisationId();
            Organisation org = organisationRepository.findByIdAndDeletedAtIsNull(orgId).orElseThrow(() -> new IllegalArgumentException("Organisation not found for tenant"));
            d = departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org).orElseThrow(() -> new IllegalArgumentException("Department not found"));
        } else {
            d = departmentRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new IllegalArgumentException("Department not found"));
        }
        d.setDeletedAt(Instant.now());
        departmentRepository.save(d);
    }

    private DepartmentDto toDto(Department d) {
        DepartmentDto dto = new DepartmentDto();
        dto.setId(d.getId());
        dto.setName(d.getName());
        if (d.getOrganisation() != null) dto.setOrganisationId(d.getOrganisation().getId());
        return dto;
    }
}
