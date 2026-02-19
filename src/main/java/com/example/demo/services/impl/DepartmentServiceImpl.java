package com.example.demo.services.impl;

import com.example.demo.dto.DepartmentDto;
import com.example.demo.models.Department;
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

        if (dto.name == null || dto.name.trim().isEmpty()) {
            throw new IllegalArgumentException("Department name is required");
        }

        String name = dto.name.trim();

        if (departmentRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(name)) {
            throw new IllegalStateException("Department with the same name already exists");
        }

        Department department = new Department();
        department.setName(name);

        if (dto.organisationId != null) {
            organisationRepository
                    .findByIdAndDeletedAtIsNull(dto.organisationId)
                    .ifPresent(department::setOrganisation);
        }

        Department saved = departmentRepository.save(department);
        return toDto(saved);
    }


    @Override
    public DepartmentDto get(UUID id) {
        return departmentRepository.findByIdAndDeletedAtIsNull(id).map(this::toDto).orElse(null);
    }

    @Override
    public List<DepartmentDto> list() {
        return departmentRepository.findAllByDeletedAtIsNull().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public DepartmentDto update(UUID id, DepartmentDto dto) {
        Department d = departmentRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new IllegalArgumentException("Department not found"));
        if (dto.name != null) d.setName(dto.name);
        if (dto.organisationId != null) organisationRepository.findByIdAndDeletedAtIsNull(dto.organisationId).ifPresent(d::setOrganisation);
        Department saved = departmentRepository.save(d);
        return toDto(saved);
    }

    @Override
    public void delete(UUID id) {
        Department d = departmentRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new IllegalArgumentException("Department not found"));
        d.setDeletedAt(Instant.now());
        departmentRepository.save(d);
    }

    private DepartmentDto toDto(Department d) {
        DepartmentDto dto = new DepartmentDto();
        dto.id = d.getId();
        dto.name = d.getName();
        if (d.getOrganisation() != null) dto.organisationId = d.getOrganisation().getId();
        return dto;
    }
}
