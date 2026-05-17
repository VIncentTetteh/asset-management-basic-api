package com.assetiq.services.impl;

import com.assetiq.dto.DepartmentDto;
import com.assetiq.config.CachingConfig;
import com.assetiq.models.Department;
import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.DepartmentRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.DepartmentService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import com.assetiq.repositories.UserRepository;
import com.assetiq.models.User;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

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
    @CacheEvict(value = CachingConfig.CacheNames.DEPARTMENTS, allEntries = true)
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

        if (dto.getDepartmentCode() != null && !dto.getDepartmentCode().isBlank()) {
            if (departmentRepository.existsByDepartmentCodeIgnoreCaseAndOrganisationAndDeletedAtIsNull(dto.getDepartmentCode(), organisation)) {
                throw new IllegalStateException("Department code \"" + dto.getDepartmentCode() + "\" is already in use in this organisation");
            }
        }

        if (dto.getCostCenterCode() != null && !dto.getCostCenterCode().isBlank()) {
            if (departmentRepository.existsByCostCenterCodeIgnoreCaseAndOrganisationAndDeletedAtIsNull(dto.getCostCenterCode(), organisation)) {
                throw new IllegalStateException("Cost center code \"" + dto.getCostCenterCode() + "\" is already in use in this organisation");
            }
        }

        Department department = new Department();
        department.setName(name);
        department.setOrganisation(organisation);

        if (dto.getDescription() != null)
            department.setDescription(dto.getDescription());
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
    @Cacheable(value = CachingConfig.CacheNames.DEPARTMENTS, key = "T(com.assetiq.multitenancy.TenantContext).getOrganisationId().toString() + ':one:' + #id.toString()", unless = "#result == null")
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
    @Cacheable(value = CachingConfig.CacheNames.DEPARTMENTS, key = "T(com.assetiq.multitenancy.TenantContext).getOrganisationId().toString() + ':list'")
    public List<DepartmentDto> list() {
        if (!TenantContext.hasOrganisationId()) {
            throw new IllegalArgumentException("Organisation context is required (X-Organisation-Id header missing)");
        }

        UUID orgId = TenantContext.getOrganisationId();
        Organisation org = organisationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organisation not found: " + orgId));

        List<Department> result = departmentRepository.findAllByOrganisationAndDeletedAtIsNull(org);
        return result.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = CachingConfig.CacheNames.DEPARTMENTS, key = "T(com.assetiq.multitenancy.TenantContext).getOrganisationId().toString() + ':children:' + #parentDepartmentId.toString()")
    public List<DepartmentDto> listSubDepartments(UUID parentDepartmentId) {
        if (!TenantContext.hasOrganisationId()) {
            throw new IllegalArgumentException("Organisation context is required (X-Organisation-Id header missing)");
        }

        UUID orgId = TenantContext.getOrganisationId();
        Organisation org = organisationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organisation not found: " + orgId));

        departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(parentDepartmentId, org)
                .orElseThrow(() -> new IllegalArgumentException("Parent department not found in your organisation"));

        List<Department> result = departmentRepository
                .findAllByOrganisationAndParentDepartmentIdAndDeletedAtIsNull(org, parentDepartmentId);
        return result.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @CacheEvict(value = CachingConfig.CacheNames.DEPARTMENTS, allEntries = true)
    public DepartmentDto update(UUID id, DepartmentDto dto) {
        if (!TenantContext.hasOrganisationId()) {
            throw new IllegalArgumentException("Organisation context is required (X-Organisation-Id header missing)");
        }

        UUID orgId = TenantContext.getOrganisationId();
        Organisation org = organisationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organisation not found: " + orgId));

        Department d = departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));

        if (dto.getName() != null && !dto.getName().equalsIgnoreCase(d.getName())) {
            if (departmentRepository.existsByNameIgnoreCaseAndOrganisationAndDeletedAtIsNull(dto.getName(), org)) {
                throw new IllegalStateException("Department with the same name already exists in this organisation");
            }
            d.setName(dto.getName());
        }
        if (dto.getDescription() != null)
            d.setDescription(dto.getDescription());
        if (dto.getDepartmentCode() != null) {
            if (!dto.getDepartmentCode().isBlank() &&
                    departmentRepository.existsByDepartmentCodeIgnoreCaseAndOrganisationAndDeletedAtIsNullAndIdNot(dto.getDepartmentCode(), org, id)) {
                throw new IllegalStateException("Department code \"" + dto.getDepartmentCode() + "\" is already in use in this organisation");
            }
            d.setDepartmentCode(dto.getDepartmentCode());
        }
        if (dto.getCostCenterCode() != null) {
            if (!dto.getCostCenterCode().isBlank() &&
                    departmentRepository.existsByCostCenterCodeIgnoreCaseAndOrganisationAndDeletedAtIsNullAndIdNot(dto.getCostCenterCode(), org, id)) {
                throw new IllegalStateException("Cost center code \"" + dto.getCostCenterCode() + "\" is already in use in this organisation");
            }
            d.setCostCenterCode(dto.getCostCenterCode());
        }
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
    @CacheEvict(value = CachingConfig.CacheNames.DEPARTMENTS, allEntries = true)
    public DepartmentDto patch(UUID id, DepartmentDto dto) {
        return update(id, dto);
    }

    @Override
    @CacheEvict(value = CachingConfig.CacheNames.DEPARTMENTS, allEntries = true)
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
        dto.setDescription(d.getDescription());
        dto.setDepartmentCode(d.getDepartmentCode());
        dto.setCostCenterCode(d.getCostCenterCode());
        dto.setBudgetLimit(d.getBudgetLimit());
        dto.setStatus(d.getStatus());
        dto.setCreatedAt(d.getCreatedAt());
        dto.setUpdatedAt(d.getUpdatedAt());

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
