package com.example.demo.services.impl;

import com.example.demo.dto.AssetDto;
import com.example.demo.enums.AssetState;
import com.example.demo.enums.AssetStatus;
import com.example.demo.models.Asset;
import com.example.demo.models.Department;
import com.example.demo.models.Organisation;
import com.example.demo.multitenancy.TenantContext;
import com.example.demo.repositories.AssetRepository;
import com.example.demo.repositories.DepartmentRepository;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.services.AssetService;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.math.BigDecimal;

@Service
public class AssetServiceImpl implements AssetService {

    private final AssetRepository assetRepository;
    private final DepartmentRepository departmentRepository;
    private final OrganisationRepository organisationRepository;

    public AssetServiceImpl(AssetRepository assetRepository, DepartmentRepository departmentRepository, OrganisationRepository organisationRepository) {
        this.assetRepository = assetRepository;
        this.departmentRepository = departmentRepository;
        this.organisationRepository = organisationRepository;
    }

    @Override
    @Transactional
    public AssetDto create(AssetDto dto) {

        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Asset name is required");
        }

        String name = dto.getName().trim();

        // Resolve organisation from tenant context when available; otherwise use dto.organisationId
        Organisation organisation = null;
        if (TenantContext.hasOrganisationId()) {
            UUID orgId = TenantContext.getOrganisationId();
            organisation = organisationRepository.findByIdAndDeletedAtIsNull(orgId)
                    .orElseThrow(() -> new IllegalArgumentException("Organisation not found for tenant header"));
        } else {
            if (dto.getOrganisationId() == null) {
                throw new IllegalArgumentException("Organisation is required");
            }
            organisation = organisationRepository.findByIdAndDeletedAtIsNull(dto.getOrganisationId())
                    .orElseThrow(() -> new IllegalArgumentException("Organisation not found"));
        }

        Department department = null;
        if (dto.getDepartmentId() != null) {
            // When tenant header is set, department must belong to the tenant organisation
            department = departmentRepository
                    .findByIdAndDeletedAtIsNull(dto.getDepartmentId())
                    .orElseThrow(() -> new IllegalArgumentException("Department not found"));
            if (TenantContext.hasOrganisationId() && !department.getOrganisation().getId().equals(organisation.getId())) {
                throw new IllegalArgumentException("Department does not belong to tenant organisation");
            }
        }

        // Check uniqueness scoped to organisation + department
        if (assetRepository.existsByNameIgnoreCaseAndOrganisationAndDepartmentAndDeletedAtIsNull(
                name, organisation, department)) {
            throw new IllegalStateException(
                    "Asset with the same name already exists in this department");
        }

        Asset asset = new Asset();
        asset.setName(name);
        if (dto.getCategoryId() != null) {
            // TODO: Load category by ID
        }
        asset.setPurchaseCost(dto.getPurchaseCost());
        asset.setUsefulLifeMonths(dto.getUsefulLifeMonths());
        asset.setOrganisation(organisation);
        asset.setDepartment(department); // can be null if department not provided

        try {
            Asset saved = assetRepository.save(asset);
            return toDto(saved);
        } catch (DataIntegrityViolationException ex) {
            // Final safety net for race conditions
            throw new IllegalStateException(
                    "Asset with the same name already exists in this department");
        }
    }

    @Override
    public AssetDto get(UUID id) {
        if (TenantContext.hasOrganisationId()) {
            UUID orgId = TenantContext.getOrganisationId();
            Organisation org = organisationRepository.findByIdAndDeletedAtIsNull(orgId).orElseThrow(() -> new IllegalArgumentException("Organisation not found for tenant"));
            return assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org).map(this::toDto).orElse(null);
        }
        return assetRepository.findByIdAndDeletedAtIsNull(id).map(this::toDto).orElse(null);
    }

    @Override
    public List<AssetDto> list() {
        if (TenantContext.hasOrganisationId()) {
            UUID orgId = TenantContext.getOrganisationId();
            Organisation org = organisationRepository.findByIdAndDeletedAtIsNull(orgId).orElseThrow(() -> new IllegalArgumentException("Organisation not found for tenant"));
            return assetRepository.findAllByOrganisationAndDeletedAtIsNull(org).stream().map(this::toDto).collect(Collectors.toList());
        }
        return assetRepository.findAllByDeletedAtIsNull().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public AssetDto assignToDepartment(UUID assetId, UUID departmentId) {
        Optional<Asset> oa = assetRepository.findByIdAndDeletedAtIsNull(assetId);
        if (oa.isEmpty()) return null;
        Asset asset = oa.get();
        if (asset.getStatus() != AssetStatus.IN_USE) {
            throw new IllegalStateException("Only assets in IN_USE status can be assigned");
        }
        Department dept = departmentRepository.findByIdAndDeletedAtIsNull(departmentId).orElseThrow(() -> new IllegalArgumentException("Department not found"));
        if (TenantContext.hasOrganisationId()) {
            UUID orgId = TenantContext.getOrganisationId();
            if (!dept.getOrganisation().getId().equals(orgId) || !asset.getOrganisation().getId().equals(orgId)) {
                throw new IllegalArgumentException("Department or asset does not belong to tenant organisation");
            }
        }
        asset.setDepartment(dept);
        // Status remains IN_USE after assignment
        Asset saved = assetRepository.save(asset);
        return toDto(saved);
    }

    @Override
    public AssetDto update(UUID id, AssetDto dto) {
        Asset asset;
        if (TenantContext.hasOrganisationId()) {
            UUID orgId = TenantContext.getOrganisationId();
            Organisation org = organisationRepository.findByIdAndDeletedAtIsNull(orgId).orElseThrow(() -> new IllegalArgumentException("Organisation not found for tenant"));
            asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                    .orElseThrow(() -> new IllegalArgumentException("Asset not found"));
        } else {
            asset = assetRepository.findByIdAndDeletedAtIsNull(id)
                    .orElseThrow(() -> new IllegalArgumentException("Asset not found"));
        }

        if (dto.getName() != null) asset.setName(dto.getName());
        if (dto.getCategoryId() != null) {
            // Load category by ID if provided
        }
        if (dto.getPurchaseCost() != null) asset.setPurchaseCost(dto.getPurchaseCost());
        if (dto.getUsefulLifeMonths() != null) asset.setUsefulLifeMonths(dto.getUsefulLifeMonths());
        if (dto.getOrganisationId() != null && !TenantContext.hasOrganisationId()) {
            organisationRepository.findByIdAndDeletedAtIsNull(dto.getOrganisationId())
                    .ifPresent(asset::setOrganisation);
        }
        if (dto.getStatus() != null) asset.setStatus(dto.getStatus());

        Asset saved = assetRepository.save(asset);
        return toDto(saved);
    }

    @Override
    public void delete(UUID id) {
        Asset a;
        if (TenantContext.hasOrganisationId()) {
            UUID orgId = TenantContext.getOrganisationId();
            Organisation org = organisationRepository.findByIdAndDeletedAtIsNull(orgId).orElseThrow(() -> new IllegalArgumentException("Organisation not found for tenant"));
            a = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org).orElseThrow(() -> new IllegalArgumentException("Asset not found"));
        } else {
            a = assetRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new IllegalArgumentException("Asset not found"));
        }
        a.setDeletedAt(Instant.now());
        assetRepository.save(a);
    }

    private AssetDto toDto(Asset a) {
        AssetDto d = new AssetDto();
        d.setId(a.getId());
        d.setName(a.getName());
        d.setCategoryId(a.getCategory() != null ? a.getCategory().getId() : null);
        d.setPurchaseCost(a.getPurchaseCost());
        d.setUsefulLifeMonths(a.getUsefulLifeMonths());
        d.setStatus(a.getStatus());
        if (a.getDepartment() != null) d.setDepartmentId(a.getDepartment().getId());
        if (a.getOrganisation() != null) d.setOrganisationId(a.getOrganisation().getId());
        return d;
    }
}
