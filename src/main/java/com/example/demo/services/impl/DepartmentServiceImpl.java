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
import com.example.demo.repositories.UserRepository;
import com.example.demo.models.User;

@Service
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final OrganisationRepository organisationRepository;
    private final UserRepository userRepository;

    public DepartmentServiceImpl(DepartmentRepository departmentRepository,
            OrganisationRepository organisationRepository,
            UserRepository userRepository) {
        this.departmentRepository = departmentRepository;
        this.organisationRepository = organisationRepository;
        this.userRepository = userRepository;
    }

    @Override
    public DepartmentDto create(DepartmentDto dto) {

        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Department name is required");
        }

        if (!TenantContext.hasOrganisationId()) {
            throw new IllegalArgumentException("Organisation context is required (X-Organisation-Id header missing)");
        }

        String name = dto.getName().trim();

        UUID orgId = TenantContext.getOrganisationId();
        Organisation organisation = organisationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organisation not found: " + orgId));

        if (departmentRepository.existsByNameIgnoreCaseAndOrganisationAndDeletedAtIsNull(name, organisation)) {
            throw new IllegalStateException("Department with the same name already exists in this organisation");
        }

        Department department = new Department();
        department.setName(name);
        department.setOrganisation(organisation);

        if (dto.getDepartmentCode() != null)
            department.setDepartmentCode(dto.getDepartmentCode());
        if (dto.getCostCenterCode() != null)
            department.setCostCenterCode(dto.getCostCenterCode());
        if (dto.getBudgetLimit() != null)
            department.setBudgetLimit(dto.getBudgetLimit());
        if (dto.getStatus() != null)
            department.setStatus(dto.getStatus());

        if (dto.getParentDepartmentId() != null) {
            Department parent = departmentRepository
                    .findByIdAndOrganisationAndDeletedAtIsNull(dto.getParentDepartmentId(), organisation)
                    .orElseThrow(() -> new IllegalArgumentException("Parent department not found"));
            department.setParentDepartment(parent);
        }

        if (dto.getManagerId() != null) {
            User manager = userRepository.findByIdAndOrganisation(dto.getManagerId(), organisation)
                    .orElseThrow(() -> new IllegalArgumentException("Manager user not found in this organisation"));
            department.setManager(manager);
        }

        Department saved = departmentRepository.save(department);
        return toDto(saved);
    }

    @Override
    public DepartmentDto get(UUID id) {
        if (!TenantContext.hasOrganisationId()) {
            throw new IllegalArgumentException("Organisation context is required (X-Organisation-Id header missing)");
        }

        UUID orgId = TenantContext.getOrganisationId();
        Organisation org = organisationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organisation not found: " + orgId));

        Department d = departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org).orElse(null);
        return d != null ? toDto(d) : null;
    }

    @Override
    public List<DepartmentDto> list() {
        if (!TenantContext.hasOrganisationId()) {
            throw new IllegalArgumentException("Organisation context is required (X-Organisation-Id header missing)");
        }

        UUID orgId = TenantContext.getOrganisationId();
        Organisation org = organisationRepository.findByIdAndDeletedAtIsNull(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organisation not found: " + orgId));

        List<Department> result = departmentRepository.findAllByOrganisationAndDeletedAtIsNull(org);
        return result.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public DepartmentDto update(UUID id, DepartmentDto dto) {
        if (!TenantContext.hasOrganisationId()) {
            throw new IllegalArgumentException("Organisation context is required (X-Organisation-Id header missing)");
        }

        UUID orgId = TenantContext.getOrganisationId();
        Organisation org = organisationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organisation not found: " + orgId));

        Department d = departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));

        if (dto.getName() != null)
            d.setName(dto.getName());
        if (dto.getDepartmentCode() != null)
            d.setDepartmentCode(dto.getDepartmentCode());
        if (dto.getCostCenterCode() != null)
            d.setCostCenterCode(dto.getCostCenterCode());
        if (dto.getBudgetLimit() != null)
            d.setBudgetLimit(dto.getBudgetLimit());
        if (dto.getStatus() != null)
            d.setStatus(dto.getStatus());

        if (dto.getParentDepartmentId() != null) {
            Department parent = departmentRepository
                    .findByIdAndOrganisationAndDeletedAtIsNull(dto.getParentDepartmentId(), org)
                    .orElseThrow(() -> new IllegalArgumentException("Parent department not found"));
            d.setParentDepartment(parent);
        }

        if (dto.getManagerId() != null) {
            User manager = userRepository.findByIdAndOrganisation(dto.getManagerId(), org)
                    .orElseThrow(() -> new IllegalArgumentException("Manager user not found in this organisation"));
            d.setManager(manager);
        }

        Department saved = departmentRepository.save(d);
        return toDto(saved);
    }

    @Override
    public DepartmentDto patch(UUID id, DepartmentDto dto) {
        return update(id, dto);
    }

    @Override
    public void delete(UUID id) {
        if (!TenantContext.hasOrganisationId()) {
            throw new IllegalArgumentException("Organisation context is required (X-Organisation-Id header missing)");
        }

        UUID orgId = TenantContext.getOrganisationId();
        Organisation org = organisationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organisation not found: " + orgId));

        Department d = departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));

        d.setDeletedAt(Instant.now());
        departmentRepository.save(d);
    }

    private DepartmentDto toDto(Department d) {
        DepartmentDto dto = new DepartmentDto();
        dto.setId(d.getId());
        dto.setName(d.getName());
        dto.setDepartmentCode(d.getDepartmentCode());
        dto.setCostCenterCode(d.getCostCenterCode());
        dto.setBudgetLimit(d.getBudgetLimit());
        dto.setStatus(d.getStatus());

        if (d.getParentDepartment() != null) {
            dto.setParentDepartmentId(d.getParentDepartment().getId());
        }
        if (d.getManager() != null) {
            dto.setManagerId(d.getManager().getId());
        }
        if (d.getOrganisation() != null) {
            dto.setOrganisationId(d.getOrganisation().getId());
        }
        return dto;
    }
}
