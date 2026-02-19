package com.example.demo.services.impl;

import com.example.demo.dto.AssetDto;
import com.example.demo.enums.AssetState;
import com.example.demo.models.Asset;
import com.example.demo.models.Department;
import com.example.demo.models.Organisation;
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

        if (dto.name == null || dto.name.trim().isEmpty()) {
            throw new IllegalArgumentException("Asset name is required");
        }

        if (dto.organisationId == null) {
            throw new IllegalArgumentException("Organisation is required");
        }

        String name = dto.name.trim();

        Organisation organisation = organisationRepository
                .findByIdAndDeletedAtIsNull(dto.organisationId)
                .orElseThrow(() -> new IllegalArgumentException("Organisation not found"));

        Department department = null;
        if (dto.departmentId != null) {
            department = departmentRepository
                    .findByIdAndDeletedAtIsNull(dto.departmentId)
                    .orElseThrow(() -> new IllegalArgumentException("Department not found"));
        }

        // Check uniqueness scoped to organisation + department
        if (assetRepository.existsByNameIgnoreCaseAndOrganisationAndDepartmentAndDeletedAtIsNull(
                name, organisation, department)) {
            throw new IllegalStateException(
                    "Asset with the same name already exists in this department");
        }

        Asset asset = new Asset();
        asset.setName(name);
        asset.setCategory(dto.category);
        asset.setPurchaseCost(dto.purchaseCost);
        asset.setUsefulLifeInYears(dto.usefulLifeInYears);
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
        return assetRepository.findByIdAndDeletedAtIsNull(id).map(this::toDto).orElse(null);
    }

    @Override
    public List<AssetDto> list() {
        return assetRepository.findAllByDeletedAtIsNull().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public AssetDto assignToDepartment(UUID assetId, UUID departmentId) {
        Optional<Asset> oa = assetRepository.findByIdAndDeletedAtIsNull(assetId);
        if (oa.isEmpty()) return null;
        Asset asset = oa.get();
        if (asset.getState() != AssetState.REGISTERED) {
            throw new IllegalStateException("Only assets in REGISTERED state can be assigned");
        }
        Department dept = departmentRepository.findByIdAndDeletedAtIsNull(departmentId).orElseThrow(() -> new IllegalArgumentException("Department not found"));
        asset.setDepartment(dept);
        asset.setState(AssetState.ASSIGNED);
        Asset saved = assetRepository.save(asset);
        return toDto(saved);
    }

    @Override
    public AssetDto update(UUID id, AssetDto dto) {
        Asset asset = assetRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found"));

        if (dto.name != null) asset.setName(dto.name);
        if (dto.category != null) asset.setCategory(dto.category);
        if (dto.purchaseCost != null) asset.setPurchaseCost(dto.purchaseCost);
        if (dto.usefulLifeInYears != null) asset.setUsefulLifeInYears(dto.usefulLifeInYears);
        if (dto.organisationId != null) {
            organisationRepository.findByIdAndDeletedAtIsNull(dto.organisationId)
                    .ifPresent(asset::setOrganisation);
        }
        if (dto.state != null) asset.setState(AssetState.valueOf(dto.state)); // <-- update status here

        Asset saved = assetRepository.save(asset);
        return toDto(saved);
    }


    @Override
    public void delete(UUID id) {
        Asset a = assetRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new IllegalArgumentException("Asset not found"));
        a.setDeletedAt(Instant.now());
        assetRepository.save(a);
    }

    private AssetDto toDto(Asset a) {
        AssetDto d = new AssetDto();
        d.id = a.getId();
        d.name = a.getName();
        d.category = a.getCategory();
        d.purchaseCost = a.getPurchaseCost();
        d.usefulLifeInYears = a.getUsefulLifeInYears();
        d.state = a.getState().name();
        if (a.getDepartment() != null) d.departmentId = a.getDepartment().getId();
        if (a.getOrganisation() != null) d.organisationId = a.getOrganisation().getId();
        return d;
    }
}
