package com.example.demo.services.impl;

import com.example.demo.dto.AssetDto;
import com.example.demo.enums.AssetStatus;
import com.example.demo.models.*;
import com.example.demo.multitenancy.TenantContext;
import com.example.demo.repositories.*;
import com.example.demo.services.AssetService;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.EnumSet;

@Service
public class AssetServiceImpl implements AssetService {

    private final AssetRepository assetRepository;
    private final DepartmentRepository departmentRepository;
    private final OrganisationRepository organisationRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    public AssetServiceImpl(AssetRepository assetRepository,
            DepartmentRepository departmentRepository,
            OrganisationRepository organisationRepository,
            CategoryRepository categoryRepository,
            LocationRepository locationRepository,
            SupplierRepository supplierRepository,
            UserRepository userRepository,
            PurchaseOrderRepository purchaseOrderRepository) {
        this.assetRepository = assetRepository;
        this.departmentRepository = departmentRepository;
        this.organisationRepository = organisationRepository;
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.supplierRepository = supplierRepository;
        this.userRepository = userRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    // ────────────────────────────────────────────────────
    // Internal helpers
    // ────────────────────────────────────────────────────

    /** Returns the current tenant Organisation or throws 403. */
    private Organisation requireTenantOrg() {
        if (!TenantContext.hasOrganisationId()) {
            throw new AccessDeniedException("Tenant context is required.");
        }
        return organisationRepository.findByIdAndDeletedAtIsNull(TenantContext.getOrganisationId())
                .orElseThrow(() -> new AccessDeniedException("Organisation not found for current tenant."));
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    // ────────────────────────────────────────────────────
    // CRUD operations
    // ────────────────────────────────────────────────────

    @Override
    @Transactional
    public AssetDto create(AssetDto dto) {
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Asset name is required");
        }

        Organisation organisation = requireTenantOrg();
        String name = dto.getName().trim();

        Department department = null;
        if (dto.getDepartmentId() != null) {
            department = departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(
                    dto.getDepartmentId(), organisation)
                    .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));
        }

        // Uniqueness check: scoped to org + department when provided, or just org when
        // no department
        boolean duplicate;
        if (department != null) {
            duplicate = assetRepository.existsByNameIgnoreCaseAndOrganisationAndDepartmentAndDeletedAtIsNull(
                    name, organisation, department);
        } else {
            duplicate = assetRepository.existsByNameIgnoreCaseAndOrganisationAndDeletedAtIsNull(name, organisation);
        }
        if (duplicate) {
            throw new IllegalStateException("Asset with the same name already exists in this organisation");
        }

        Asset asset = new Asset();
        asset.setName(name);
        asset.setOrganisation(organisation);
        asset.setDepartment(department);

        // Map all DTO fields
        asset.setAssetTag(dto.getAssetTag());
        asset.setSerialNumber(dto.getSerialNumber());
        asset.setBarcodeQrCode(dto.getBarcodeQrCode());
        asset.setDescription(dto.getDescription());
        asset.setAssetType(dto.getAssetType());
        asset.setManufacturer(dto.getManufacturer());
        asset.setModel(dto.getModel());
        asset.setPurchaseDate(dto.getPurchaseDate());
        asset.setPurchaseCost(dto.getPurchaseCost());
        if (dto.getCurrency() != null)
            asset.setCurrency(dto.getCurrency());
        asset.setDepreciationMethod(dto.getDepreciationMethod());
        asset.setUsefulLifeMonths(dto.getUsefulLifeMonths());
        asset.setResidualValue(dto.getResidualValue());
        asset.setWarrantyExpiryDate(dto.getWarrantyExpiryDate());
        if (dto.getStatus() != null)
            asset.setStatus(dto.getStatus());
        if (dto.getCondition() != null)
            asset.setCondition(dto.getCondition());
        asset.setInvoiceId(dto.getInvoiceId());
        asset.setInsurancePolicyId(dto.getInsurancePolicyId());

        if (dto.getCategoryId() != null) {
            categoryRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getCategoryId(), organisation)
                    .ifPresent(asset::setCategory);
        }
        if (dto.getLocationId() != null) {
            locationRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getLocationId(), organisation)
                    .ifPresent(asset::setLocation);
        }
        if (dto.getSupplierId() != null) {
            supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getSupplierId(), organisation)
                    .ifPresent(asset::setSupplier);
        }
        if (dto.getAssignedUserId() != null) {
            userRepository.findByIdAndOrganisation(dto.getAssignedUserId(), organisation)
                    .ifPresent(asset::setAssignedUser);
        }
        if (dto.getPurchaseOrderId() != null) {
            purchaseOrderRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getPurchaseOrderId(), organisation)
                    .ifPresent(asset::setPurchaseOrder);
        }

        try {
            return toDto(assetRepository.save(asset));
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException("Asset with the same name already exists in this department");
        }
    }

    @Override
    public AssetDto get(UUID id) {
        Organisation org = requireTenantOrg();
        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org).orElse(null);
        return asset != null ? toDto(asset) : null;
    }

    @Override
    public List<AssetDto> list() {
        Organisation org = requireTenantOrg();
        return assetRepository.findAllByOrganisationAndDeletedAtIsNull(org)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public AssetDto assignToDepartment(UUID assetId, UUID departmentId) {
        Organisation org = requireTenantOrg();
        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found"));
        // Allow assignment from any active status; only block retired/disposed/missing
        // states
        Set<AssetStatus> assignable = EnumSet.of(
                AssetStatus.IN_STOCK, AssetStatus.RESERVED, AssetStatus.IN_USE);
        if (!assignable.contains(asset.getStatus())) {
            throw new IllegalStateException(
                    "Asset cannot be assigned in its current status: " + asset.getStatus());
        }
        Department dept = departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(departmentId, org)
                .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));
        asset.setDepartment(dept);
        return toDto(assetRepository.save(asset));
    }

    @Override
    @Transactional
    public AssetDto update(UUID id, AssetDto dto) {
        Organisation org = requireTenantOrg();
        // ROLE_ADMIN can update any asset in the org; ROLE_USER cannot write
        if (!isAdmin()) {
            throw new AccessDeniedException("Only administrators can update assets");
        }
        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found"));

        if (dto.getName() != null)
            asset.setName(dto.getName());
        if (dto.getAssetTag() != null)
            asset.setAssetTag(dto.getAssetTag());
        if (dto.getSerialNumber() != null)
            asset.setSerialNumber(dto.getSerialNumber());
        if (dto.getBarcodeQrCode() != null)
            asset.setBarcodeQrCode(dto.getBarcodeQrCode());
        if (dto.getDescription() != null)
            asset.setDescription(dto.getDescription());
        if (dto.getAssetType() != null)
            asset.setAssetType(dto.getAssetType());
        if (dto.getManufacturer() != null)
            asset.setManufacturer(dto.getManufacturer());
        if (dto.getModel() != null)
            asset.setModel(dto.getModel());
        if (dto.getPurchaseDate() != null)
            asset.setPurchaseDate(dto.getPurchaseDate());
        if (dto.getPurchaseCost() != null)
            asset.setPurchaseCost(dto.getPurchaseCost());
        if (dto.getCurrency() != null)
            asset.setCurrency(dto.getCurrency());
        if (dto.getDepreciationMethod() != null)
            asset.setDepreciationMethod(dto.getDepreciationMethod());
        if (dto.getUsefulLifeMonths() != null)
            asset.setUsefulLifeMonths(dto.getUsefulLifeMonths());
        if (dto.getResidualValue() != null)
            asset.setResidualValue(dto.getResidualValue());
        if (dto.getWarrantyExpiryDate() != null)
            asset.setWarrantyExpiryDate(dto.getWarrantyExpiryDate());
        if (dto.getStatus() != null)
            asset.setStatus(dto.getStatus());
        if (dto.getCondition() != null)
            asset.setCondition(dto.getCondition());
        if (dto.getInvoiceId() != null)
            asset.setInvoiceId(dto.getInvoiceId());
        if (dto.getInsurancePolicyId() != null)
            asset.setInsurancePolicyId(dto.getInsurancePolicyId());

        if (dto.getCategoryId() != null) {
            categoryRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getCategoryId(), org)
                    .ifPresent(asset::setCategory);
        }
        if (dto.getDepartmentId() != null) {
            departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getDepartmentId(), org)
                    .ifPresent(asset::setDepartment);
        }
        if (dto.getLocationId() != null) {
            locationRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getLocationId(), org)
                    .ifPresent(asset::setLocation);
        }
        if (dto.getSupplierId() != null) {
            supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getSupplierId(), org)
                    .ifPresent(asset::setSupplier);
        }
        if (dto.getAssignedUserId() != null) {
            userRepository.findByIdAndOrganisation(dto.getAssignedUserId(), org)
                    .ifPresent(asset::setAssignedUser);
        }
        if (dto.getPurchaseOrderId() != null) {
            purchaseOrderRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getPurchaseOrderId(), org)
                    .ifPresent(asset::setPurchaseOrder);
        }

        return toDto(assetRepository.save(asset));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Organisation org = requireTenantOrg();
        // Only admins may delete
        if (!isAdmin()) {
            throw new AccessDeniedException("Only administrators can delete assets");
        }
        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found"));
        asset.setDeletedAt(Instant.now());
        assetRepository.save(asset);
    }

    // ────────────────────────────────────────────────────
    // Filtered list queries
    // ────────────────────────────────────────────────────

    @Override
    public Set<AssetDto> listByStatus(AssetStatus status) {
        Organisation org = requireTenantOrg();
        return assetRepository.findByOrganisationIdAndStatusAndDeletedAtIsNull(org.getId(), status)
                .stream().map(this::toDto).collect(Collectors.toSet());
    }

    @Override
    public Set<AssetDto> listByDepartment(UUID departmentId) {
        Organisation org = requireTenantOrg();
        // Ensure the department belongs to this tenant
        departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(departmentId, org)
                .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));
        return assetRepository.findByDepartmentIdAndDeletedAtIsNull(departmentId)
                .stream().map(this::toDto).collect(Collectors.toSet());
    }

    @Override
    public Set<AssetDto> listByCategory(UUID categoryId) {
        Organisation org = requireTenantOrg();
        // Ensure category belongs to this tenant
        categoryRepository.findByIdAndOrganisationAndDeletedAtIsNull(categoryId, org)
                .orElseThrow(() -> new IllegalArgumentException("Category not found in your organisation"));
        return assetRepository.findByCategoryIdAndDeletedAtIsNull(categoryId)
                .stream().map(this::toDto).collect(Collectors.toSet());
    }

    // ────────────────────────────────────────────────────
    // DTO conversion
    // ────────────────────────────────────────────────────

    private AssetDto toDto(Asset a) {
        AssetDto d = new AssetDto();
        d.setId(a.getId());
        d.setName(a.getName());
        d.setAssetTag(a.getAssetTag());
        d.setSerialNumber(a.getSerialNumber());
        d.setBarcodeQrCode(a.getBarcodeQrCode());
        d.setDescription(a.getDescription());
        d.setAssetType(a.getAssetType());
        d.setManufacturer(a.getManufacturer());
        d.setModel(a.getModel());
        d.setPurchaseDate(a.getPurchaseDate());
        d.setPurchaseCost(a.getPurchaseCost());
        d.setCurrency(a.getCurrency());
        d.setDepreciationMethod(a.getDepreciationMethod());
        d.setUsefulLifeMonths(a.getUsefulLifeMonths());
        d.setResidualValue(a.getResidualValue());
        d.setCurrentBookValue(a.getCurrentBookValue());
        d.setWarrantyExpiryDate(a.getWarrantyExpiryDate());
        d.setStatus(a.getStatus());
        d.setCondition(a.getCondition());
        d.setInvoiceId(a.getInvoiceId());
        d.setInsurancePolicyId(a.getInsurancePolicyId());
        if (a.getCategory() != null)
            d.setCategoryId(a.getCategory().getId());
        if (a.getDepartment() != null)
            d.setDepartmentId(a.getDepartment().getId());
        if (a.getOrganisation() != null)
            d.setOrganisationId(a.getOrganisation().getId());
        if (a.getLocation() != null)
            d.setLocationId(a.getLocation().getId());
        if (a.getSupplier() != null)
            d.setSupplierId(a.getSupplier().getId());
        if (a.getAssignedUser() != null)
            d.setAssignedUserId(a.getAssignedUser().getId());
        if (a.getPurchaseOrder() != null)
            d.setPurchaseOrderId(a.getPurchaseOrder().getId());
        return d;
    }
}
