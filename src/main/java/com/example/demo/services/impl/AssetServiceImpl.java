package com.example.demo.services.impl;

import com.example.demo.dto.AssetDto;
import com.example.demo.dto.AssetHistoryEventDto;
import com.example.demo.dto.TcoDto;
import com.example.demo.enums.AssetStatus;
import com.example.demo.models.*;
import com.example.demo.multitenancy.TenantContext;
import com.example.demo.repositories.*;
import com.example.demo.enums.NotificationType;
import com.example.demo.services.AssetService;
import com.example.demo.services.NotificationService;
import com.example.demo.services.UsageLimitService;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
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
    private final EntityManager entityManager;
    private final UsageLimitService usageLimitService;
    private final AuditEventRepository auditEventRepository;
    private final AssetTransferRepository assetTransferRepository;
    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final DisposalRecordRepository disposalRecordRepository;
    private final NotificationService notificationService;

    public AssetServiceImpl(AssetRepository assetRepository,
            DepartmentRepository departmentRepository,
            OrganisationRepository organisationRepository,
            CategoryRepository categoryRepository,
            LocationRepository locationRepository,
            SupplierRepository supplierRepository,
            UserRepository userRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            EntityManager entityManager,
            UsageLimitService usageLimitService,
            AuditEventRepository auditEventRepository,
            AssetTransferRepository assetTransferRepository,
            MaintenanceRecordRepository maintenanceRecordRepository,
            DisposalRecordRepository disposalRecordRepository,
            NotificationService notificationService) {
        this.assetRepository = assetRepository;
        this.departmentRepository = departmentRepository;
        this.organisationRepository = organisationRepository;
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.supplierRepository = supplierRepository;
        this.userRepository = userRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.entityManager = entityManager;
        this.usageLimitService = usageLimitService;
        this.auditEventRepository = auditEventRepository;
        this.assetTransferRepository = assetTransferRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.disposalRecordRepository = disposalRecordRepository;
        this.notificationService = notificationService;
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
        usageLimitService.assertCanCreateAsset(organisation);
        String name = dto.getName().trim();

        Department department = null;
        if (dto.getDepartmentId() != null) {
            department = departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(
                    dto.getDepartmentId(), organisation)
                    .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));
        }

        // Uniqueness check: scoped to org + department when provided, or just org when no department
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
        asset.setProcurementType(dto.getProcurementType());
        asset.setCostCenter(dto.getCostCenter());

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
            if (dto.getStatus() == AssetStatus.DISPOSED) {
                throw new IllegalArgumentException("Cannot assign a disposed asset to a user");
            }
            userRepository.findByIdAndOrganisation(dto.getAssignedUserId(), organisation)
                    .ifPresent(asset::setAssignedUser);
        }
        if (dto.getPurchaseOrderId() != null) {
            purchaseOrderRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getPurchaseOrderId(), organisation)
                    .ifPresent(asset::setPurchaseOrder);
        }

        try {
            Asset saved = assetRepository.save(asset);
            notificationService.notifyOrgAdmins(organisation, NotificationType.SYSTEM,
                    "New Asset Created",
                    "Asset '" + saved.getName() + "' (tag: " + saved.getAssetTag() + ") has been added to the inventory.",
                    saved.getId(), "/api/v1/assets/" + saved.getId());
            // Create DTO directly without loading related entities to avoid deep joins
            AssetDto result = new AssetDto();
            result.setId(saved.getId());
            result.setName(saved.getName());
            result.setAssetTag(saved.getAssetTag());
            result.setSerialNumber(saved.getSerialNumber());
            result.setBarcodeQrCode(saved.getBarcodeQrCode());
            result.setDescription(saved.getDescription());
            result.setAssetType(saved.getAssetType());
            result.setManufacturer(saved.getManufacturer());
            result.setModel(saved.getModel());
            result.setPurchaseDate(saved.getPurchaseDate());
            result.setPurchaseCost(saved.getPurchaseCost());
            result.setCurrency(saved.getCurrency());
            result.setDepreciationMethod(saved.getDepreciationMethod());
            result.setUsefulLifeMonths(saved.getUsefulLifeMonths());
            result.setResidualValue(saved.getResidualValue());
            result.setWarrantyExpiryDate(saved.getWarrantyExpiryDate());
            result.setStatus(saved.getStatus());
            result.setCondition(saved.getCondition());
            result.setInvoiceId(saved.getInvoiceId());
            result.setInsurancePolicyId(saved.getInsurancePolicyId());
            result.setProcurementType(saved.getProcurementType());
            result.setCostCenter(saved.getCostCenter());

            // Set IDs from the DTO input or saved entity
            result.setCategoryId(dto.getCategoryId());
            result.setDepartmentId(dto.getDepartmentId());
            result.setOrganisationId(organisation.getId());
            result.setLocationId(dto.getLocationId());
            result.setSupplierId(dto.getSupplierId());
            result.setAssignedUserId(dto.getAssignedUserId());
            result.setPurchaseOrderId(dto.getPurchaseOrderId());

            return result;
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
    public AssetDto assignToUser(UUID assetId, UUID userId) {
        Organisation org = requireTenantOrg();
        if (!isAdmin()) {
            throw new AccessDeniedException("Only administrators can assign assets to users");
        }

        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found"));

        if (asset.getStatus() == AssetStatus.DISPOSED) {
            throw new IllegalArgumentException("Cannot assign a disposed asset to a user");
        }

        User user = userRepository.findByIdAndOrganisation(userId, org)
                .orElseThrow(() -> new IllegalArgumentException("User not found in your organisation"));

        asset.setAssignedUser(user);
        if (asset.getStatus() == AssetStatus.IN_STOCK || asset.getStatus() == AssetStatus.RESERVED) {
            asset.setStatus(AssetStatus.IN_USE);
        }
        return toDto(assetRepository.save(asset));
    }

    @Override
    public AssetDto unassignUser(UUID assetId) {
        Organisation org = requireTenantOrg();
        if (!isAdmin()) {
            throw new AccessDeniedException("Only administrators can unassign assets from users");
        }

        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found"));
        asset.setAssignedUser(null);
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
        if (dto.getProcurementType() != null)
            asset.setProcurementType(dto.getProcurementType());
        if (dto.getCostCenter() != null)
            asset.setCostCenter(dto.getCostCenter());

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
            AssetStatus effectiveStatus = dto.getStatus() != null ? dto.getStatus() : asset.getStatus();
            if (effectiveStatus == AssetStatus.DISPOSED) {
                throw new IllegalArgumentException("Cannot assign a disposed asset to a user");
            }
            userRepository.findByIdAndOrganisation(dto.getAssignedUserId(), org)
                    .ifPresent(asset::setAssignedUser);
        }
        if (dto.getPurchaseOrderId() != null) {
            purchaseOrderRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getPurchaseOrderId(), org)
                    .ifPresent(asset::setPurchaseOrder);
        }

        Asset saved = assetRepository.save(asset);
        notificationService.notifyOrgAdmins(org, NotificationType.SYSTEM,
                "Asset Updated",
                "Asset '" + saved.getName() + "' has been updated.",
                saved.getId(), "/api/v1/assets/" + saved.getId());
        return toDto(saved);
    }

    @Override
    @Transactional
    public AssetDto patch(UUID id, AssetDto dto) {
        return update(id, dto);
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
        String assetName = asset.getName();
        UUID assetId = asset.getId();
        asset.setDeletedAt(Instant.now());
        assetRepository.save(asset);
        notificationService.notifyOrgAdmins(org, NotificationType.SYSTEM,
                "Asset Deleted",
                "Asset '" + assetName + "' has been removed from the inventory.",
                assetId, "/api/v1/assets");
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
        return assetRepository.findByOrganisationAndCategoryIdAndDeletedAtIsNull(org, categoryId)
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
        d.setProcurementType(a.getProcurementType());
        d.setCostCenter(a.getCostCenter());
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

    // ────────────────────────────────────────────────────
    // Asset History Timeline
    // ────────────────────────────────────────────────────

    @Override
    public List<AssetHistoryEventDto> getHistory(UUID assetId) {
        Organisation org = requireTenantOrg();

        // Validate the asset belongs to this org
        assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));

        List<AssetHistoryEventDto> timeline = new ArrayList<>();

        // 1. Audit events where path contains the asset UUID
        auditEventRepository
                .findByOrganisationAndAssetIdInPath(org, assetId.toString())
                .forEach(e -> timeline.add(AssetHistoryEventDto.ofAudit(
                        e.getId(),
                        e.getCreatedAt() != null ? e.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDateTime() : null,
                        e.getActorEmail(),
                        e.getMethod(),
                        e.getPath(),
                        e.getResponseStatus())));

        // 2. Transfers
        assetTransferRepository.findByAssetIdAndDeletedAtIsNull(assetId)
                .forEach(t -> timeline.add(AssetHistoryEventDto.ofTransfer(
                        t.getId(),
                        t.getCreatedAt() != null ? t.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDateTime() : null,
                        t.getRequestedBy() != null ? t.getRequestedBy().getEmail() : null,
                        t.getFromDepartment() != null ? t.getFromDepartment().getName() : null,
                        t.getToDepartment() != null ? t.getToDepartment().getName() : null,
                        t.getFromLocation() != null ? t.getFromLocation().getName() : null,
                        t.getToLocation() != null ? t.getToLocation().getName() : null,
                        t.getStatus() != null ? t.getStatus().name() : null)));

        // 3. Maintenance records
        maintenanceRecordRepository.findByAssetIdAndDeletedAtIsNull(assetId)
                .forEach(m -> timeline.add(AssetHistoryEventDto.ofMaintenance(
                        m.getId(),
                        m.getCreatedAt() != null ? m.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDateTime() : null,
                        m.getMaintenanceType() != null ? m.getMaintenanceType().name() : null,
                        m.getStatus() != null ? m.getStatus().name() : null,
                        m.getScheduledDate(),
                        m.getPerformedDate())));

        // 4. Disposal records
        disposalRecordRepository.findByAssetIdAndDeletedAtIsNull(assetId)
                .forEach(d -> timeline.add(AssetHistoryEventDto.ofDisposal(
                        d.getId(),
                        d.getCreatedAt() != null ? d.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDateTime() : null,
                        d.getApprovedBy() != null ? d.getApprovedBy().getEmail() : null,
                        d.getDisposalMethod() != null ? d.getDisposalMethod().name() : null,
                        d.getDisposalDate())));

        // Sort chronologically descending (most recent first)
        timeline.sort(Comparator.comparing(AssetHistoryEventDto::getOccurredAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return timeline;
    }

    // ────────────────────────────────────────────────────
    // TCO Calculation
    // ────────────────────────────────────────────────────

    @Override
    public TcoDto getTco(UUID assetId) {
        Organisation org = requireTenantOrg();
        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));

        // 1. Acquisition cost
        BigDecimal acquisitionCost = asset.getPurchaseCost() != null ? asset.getPurchaseCost() : BigDecimal.ZERO;

        // 2. Maintenance costs
        Set<com.example.demo.models.MaintenanceRecord> maintenanceRecords =
                maintenanceRecordRepository.findByAssetIdAndDeletedAtIsNull(assetId);
        BigDecimal totalMaintenanceCost = maintenanceRecords.stream()
                .filter(m -> m.getCost() != null)
                .map(com.example.demo.models.MaintenanceRecord::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int maintenanceRecordCount = maintenanceRecords.size();

        // 3. Insurance costs (annual premium * years owned)
        BigDecimal totalInsuranceCost = BigDecimal.ZERO;
        if (asset.getInsurancePremiumPerYear() != null && asset.getPurchaseDate() != null) {
            long daysOwned = java.time.temporal.ChronoUnit.DAYS.between(
                    asset.getPurchaseDate(), java.time.LocalDate.now());
            BigDecimal yearsOwned = BigDecimal.valueOf(daysOwned).divide(BigDecimal.valueOf(365), 4,
                    java.math.RoundingMode.HALF_UP);
            totalInsuranceCost = asset.getInsurancePremiumPerYear().multiply(yearsOwned)
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        }

        // 4. Downtime costs
        BigDecimal totalDowntimeCost = BigDecimal.ZERO;
        long downtimeDays = 0L;
        if (asset.getDowntimeCostPerDay() != null) {
            downtimeDays = maintenanceRecords.stream()
                    .filter(m -> m.getScheduledDate() != null && m.getPerformedDate() != null)
                    .mapToLong(m -> java.time.temporal.ChronoUnit.DAYS.between(
                            m.getScheduledDate(), m.getPerformedDate()))
                    .filter(d -> d > 0)
                    .sum();
            totalDowntimeCost = asset.getDowntimeCostPerDay()
                    .multiply(BigDecimal.valueOf(downtimeDays))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        }

        // 5. Disposal/sale recovery
        BigDecimal disposalRecovery = disposalRecordRepository.findByAssetIdAndDeletedAtIsNull(assetId)
                .stream()
                .filter(d -> d.getSaleValue() != null)
                .map(com.example.demo.models.DisposalRecord::getSaleValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 6. Net TCO = acquisition + maintenance + insurance + downtime - recovery
        BigDecimal netTco = acquisitionCost
                .add(totalMaintenanceCost)
                .add(totalInsuranceCost)
                .add(totalDowntimeCost)
                .subtract(disposalRecovery);

        TcoDto dto = new TcoDto();
        dto.setAssetId(asset.getId());
        dto.setAssetName(asset.getName());
        dto.setAssetTag(asset.getAssetTag());
        dto.setAcquisitionCost(acquisitionCost);
        dto.setTotalMaintenanceCost(totalMaintenanceCost);
        dto.setTotalInsuranceCost(totalInsuranceCost);
        dto.setTotalDowntimeCost(totalDowntimeCost);
        dto.setDisposalRecovery(disposalRecovery);
        dto.setNetTco(netTco);
        dto.setCurrency(asset.getCurrency());
        dto.setCalculatedAt(Instant.now());
        dto.setMaintenanceRecordCount(maintenanceRecordCount);
        dto.setDowntimeDays(downtimeDays);
        return dto;
    }

    // ────────────────────────────────────────────────────
    // QR Scan Lookup
    // ────────────────────────────────────────────────────

    @Override
    public AssetDto getByQrPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("QR payload is required");
        }
        // Payload format: "asset:<uuid>"
        String trimmed = payload.trim();
        UUID assetId;
        try {
            String uuidStr = trimmed.startsWith("asset:") ? trimmed.substring(6) : trimmed;
            assetId = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid QR payload: " + payload);
        }
        AssetDto result = get(assetId);
        if (result == null) {
            throw new IllegalArgumentException("Asset not found for QR payload: " + payload);
        }
        return result;
    }
}
