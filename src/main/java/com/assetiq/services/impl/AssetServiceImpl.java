package com.assetiq.services.impl;

import com.assetiq.assets.CategoryAssetDefaults;
import com.assetiq.services.HierarchyGuard;

import com.assetiq.dto.AssetDto;
import com.assetiq.dto.AssetFilterRequest;
import com.assetiq.dto.AssetHistoryEventDto;
import com.assetiq.dto.AssetStatsDto;
import com.assetiq.dto.PagedResponseDto;
import com.assetiq.dto.TcoDto;
import com.assetiq.repositories.AssetSpecification;
import com.assetiq.enums.AssetStatus;
import com.assetiq.models.*;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.*;
import com.assetiq.enums.CheckoutStatus;
import com.assetiq.enums.NotificationType;
import com.assetiq.assets.AssetLabels;
import com.assetiq.exceptions.ResourceNotFoundException;
import com.assetiq.services.finance.DepreciationCalculator;
import com.assetiq.services.money.CurrencyConversion;
import com.assetiq.services.money.MoneyAccumulator;
import com.assetiq.services.money.MoneyAggregator;
import com.assetiq.services.AssetService;
import com.assetiq.services.CurrencyResolver;
import com.assetiq.services.EmailService;
import com.assetiq.services.NotificationService;
import com.assetiq.services.UsageLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.EnumSet;

@Service
public class AssetServiceImpl implements AssetService {

    private static final Logger log = LoggerFactory.getLogger(AssetServiceImpl.class);
    private static final DateTimeFormatter EMAIL_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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
    private final EmailService emailService;
    private final CurrencyResolver currencyResolver;
    private final MoneyAggregator moneyAggregator;
    private final CheckoutRecordRepository checkoutRecordRepository;

    @Value("${app.email.base-url:http://localhost:3000}")
    private String baseUrl;

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
            NotificationService notificationService,
            EmailService emailService,
            CurrencyResolver currencyResolver,
            MoneyAggregator moneyAggregator,
            CheckoutRecordRepository checkoutRecordRepository) {
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
        this.emailService = emailService;
        this.currencyResolver = currencyResolver;
        this.moneyAggregator = moneyAggregator;
        this.checkoutRecordRepository = checkoutRecordRepository;
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

    /** Authorities allowed to edit an asset; mirrors {@code AssetController} PUT/PATCH. */
    static final Set<String> EDIT_AUTHORITIES = Set.of("ROLE_ADMIN", "ROLE_ORG_ADMIN", "EDIT_ASSET");
    /** Authorities allowed to delete an asset; mirrors {@code AssetController} DELETE. */
    static final Set<String> DELETE_AUTHORITIES = Set.of("ROLE_ADMIN", "ROLE_ORG_ADMIN", "DELETE_ASSET");
    /** Authorities allowed to (un)assign an asset to a user; mirrors the assign-user endpoints. */
    static final Set<String> ASSIGN_AUTHORITIES =
            Set.of("ROLE_ADMIN", "ROLE_ORG_ADMIN", "EDIT_ASSET", "TRANSFER_ASSET");

    /**
     * Optional fields an update may clear explicitly through {@link AssetDto#getClearFields()},
     * each with the DTO value that must not be sent alongside the clear and the setter that
     * clears it. Clearing a depreciation override (method, life, residual) makes the asset fall
     * back to its category's depreciation policy.
     */
    private static final Map<String, ClearableField> CLEARABLE = Map.ofEntries(
            Map.entry("departmentId", new ClearableField(AssetDto::getDepartmentId, a -> a.setDepartment(null))),
            Map.entry("locationId", new ClearableField(AssetDto::getLocationId, a -> a.setLocation(null))),
            Map.entry("supplierId", new ClearableField(AssetDto::getSupplierId, a -> a.setSupplier(null))),
            Map.entry("purchaseOrderId", new ClearableField(AssetDto::getPurchaseOrderId, a -> a.setPurchaseOrder(null))),
            Map.entry("assignedUserId", new ClearableField(AssetDto::getAssignedUserId, a -> a.setAssignedUser(null))),
            Map.entry("parentAssetId", new ClearableField(AssetDto::getParentAssetId, a -> a.setParentAsset(null))),
            Map.entry("categoryId", new ClearableField(AssetDto::getCategoryId, a -> a.setCategory(null))),
            Map.entry("insurancePremiumPerYear",
                    new ClearableField(AssetDto::getInsurancePremiumPerYear, a -> a.setInsurancePremiumPerYear(null))),
            Map.entry("downtimeCostPerDay",
                    new ClearableField(AssetDto::getDowntimeCostPerDay, a -> a.setDowntimeCostPerDay(null))),
            Map.entry("insurancePolicyExpiry",
                    new ClearableField(AssetDto::getInsurancePolicyExpiry, a -> a.setInsurancePolicyExpiry(null))),
            Map.entry("depreciationMethod",
                    new ClearableField(AssetDto::getDepreciationMethod, a -> a.setDepreciationMethod(null))),
            Map.entry("usefulLifeMonths",
                    new ClearableField(AssetDto::getUsefulLifeMonths, a -> a.setUsefulLifeMonths(null))),
            Map.entry("residualValue", new ClearableField(AssetDto::getResidualValue, a -> a.setResidualValue(null))),
            Map.entry("purchaseCost", new ClearableField(AssetDto::getPurchaseCost, a -> a.setPurchaseCost(null))),
            Map.entry("purchaseDate", new ClearableField(AssetDto::getPurchaseDate, a -> a.setPurchaseDate(null))),
            Map.entry("warrantyExpiryDate",
                    new ClearableField(AssetDto::getWarrantyExpiryDate, a -> a.setWarrantyExpiryDate(null))),
            Map.entry("assetTag", new ClearableField(AssetDto::getAssetTag, a -> a.setAssetTag(null))),
            Map.entry("serialNumber", new ClearableField(AssetDto::getSerialNumber, a -> a.setSerialNumber(null))),
            Map.entry("manufacturer", new ClearableField(AssetDto::getManufacturer, a -> a.setManufacturer(null))),
            Map.entry("model", new ClearableField(AssetDto::getModel, a -> a.setModel(null))),
            Map.entry("description", new ClearableField(AssetDto::getDescription, a -> a.setDescription(null))),
            Map.entry("invoiceId", new ClearableField(AssetDto::getInvoiceId, a -> a.setInvoiceId(null))),
            Map.entry("insurancePolicyId",
                    new ClearableField(AssetDto::getInsurancePolicyId, a -> a.setInsurancePolicyId(null))),
            Map.entry("costCenter", new ClearableField(AssetDto::getCostCenter, a -> a.setCostCenter(null))),
            Map.entry("procurementType",
                    new ClearableField(AssetDto::getProcurementType, a -> a.setProcurementType(null))));

    /** Names accepted in {@link AssetDto#getClearFields()}. */
    static final Set<String> CLEARABLE_FIELDS = CLEARABLE.keySet();

    /** How one clearable field is read from the request and cleared on the entity. */
    private record ClearableField(java.util.function.Function<AssetDto, Object> requested,
                                  java.util.function.Consumer<Asset> clear) {
    }

    private static boolean hasAnyAuthority(Set<String> allowed) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> allowed.contains(a.getAuthority()));
    }

    /** Recompute and store the book value from the single depreciation engine. */
    private static void refreshBookValue(Asset asset) {
        asset.setCurrentBookValue(DepreciationCalculator.forAsset(asset, LocalDate.now()).netBookValue());
    }

    /** Fill the read-only depreciation fields of {@code d} from a live calculation. */
    private static void applyDepreciation(AssetDto d, Asset a) {
        DepreciationCalculator.Result r = DepreciationCalculator.forAsset(a, LocalDate.now());
        d.setCurrentBookValue(r.netBookValue());
        d.setAccumulatedDepreciation(r.accumulatedDepreciation());
        d.setMonthlyDepreciation(r.monthlyDepreciation());
        d.setDepreciationConfigured(r.configured());
        d.setFullyDepreciated(r.fullyDepreciated());
        d.setEffectiveDepreciationMethod(r.method());
        d.setEffectiveUsefulLifeMonths(r.usefulLifeMonths());
        d.setEffectiveResidualValue(r.residualValue());
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
        // P1-2: Fall through to tenant billing currency when caller omits the value
        // so we never silently stamp a hard-coded USD / GHS onto cross-currency tenants.
        asset.setCurrency(currencyResolver.resolveOrDefault(dto.getCurrency()));
        asset.setDepreciationMethod(dto.getDepreciationMethod());
        asset.setUsefulLifeMonths(dto.getUsefulLifeMonths());
        asset.setResidualValue(dto.getResidualValue());
        asset.setWarrantyExpiryDate(dto.getWarrantyExpiryDate());
        requireEditableStatus(null, dto.getStatus());
        if (dto.getStatus() != null)
            asset.setStatus(dto.getStatus());
        if (dto.getCondition() != null)
            asset.setCondition(dto.getCondition());
        asset.setInvoiceId(dto.getInvoiceId());
        asset.setInsurancePolicyId(dto.getInsurancePolicyId());
        asset.setProcurementType(dto.getProcurementType());
        asset.setCostCenter(dto.getCostCenter());
        asset.setInsurancePremiumPerYear(dto.getInsurancePremiumPerYear());
        asset.setDowntimeCostPerDay(dto.getDowntimeCostPerDay());
        asset.setInsurancePolicyExpiry(dto.getInsurancePolicyExpiry());
        if (dto.getParentAssetId() != null) {
            asset.setParentAsset(requireParentAsset(dto.getParentAssetId(), null, organisation));
        }

        if (dto.getCategoryId() != null) {
            categoryRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getCategoryId(), organisation)
                    .ifPresent(asset::setCategory);
        }
        applyCategoryDefaults(asset, organisation);
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

        refreshBookValue(asset);

        try {
            Asset saved = assetRepository.save(asset);
            notificationService.notifyOrgAdmins(organisation, NotificationType.SYSTEM,
                    "New Asset Created",
                    AssetLabels.describe(saved) + " has been added to the inventory.",
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
            result.setInsurancePremiumPerYear(saved.getInsurancePremiumPerYear());
            result.setDowntimeCostPerDay(saved.getDowntimeCostPerDay());
            result.setInsurancePolicyExpiry(saved.getInsurancePolicyExpiry());
            result.setParentAssetId(dto.getParentAssetId());

            // Set IDs from the DTO input or saved entity
            result.setCategoryId(dto.getCategoryId());
            result.setDepartmentId(dto.getDepartmentId());
            result.setOrganisationId(organisation.getId());
            result.setLocationId(dto.getLocationId());
            result.setSupplierId(dto.getSupplierId());
            result.setAssignedUserId(dto.getAssignedUserId());
            result.setPurchaseOrderId(dto.getPurchaseOrderId());
            applyDepreciation(result, saved);

            return result;
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException("Asset with the same name already exists in this department");
        }
    }

    /**
     * Applies what the asset's category promises when the caller left the field
     * empty: the next {@code PREFIX-0001} style tag from the category's prefix code,
     * and a warranty expiry of purchase date plus the category's default warranty.
     */
    private void applyCategoryDefaults(Asset asset, Organisation organisation) {
        Category category = asset.getCategory();
        if (category == null) return;
        String prefix = CategoryAssetDefaults.prefixOf(category);
        if (prefix != null && (asset.getAssetTag() == null || asset.getAssetTag().isBlank())) {
            asset.setAssetTag(CategoryAssetDefaults.nextTag(prefix,
                    assetRepository.findAssetTagsStartingWith(organisation, prefix + "-")));
        }
        if (asset.getWarrantyExpiryDate() == null) {
            asset.setWarrantyExpiryDate(CategoryAssetDefaults.warrantyExpiry(category, asset.getPurchaseDate()));
        }
    }

    @Override
    @Transactional
    public AssetDto get(UUID id) {
        Organisation org = requireTenantOrg();
        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org).orElse(null);
        return asset != null ? toDto(asset) : null;
    }

    @Override
    @Transactional
    public List<AssetDto> list() {
        Organisation org = requireTenantOrg();
        return assetRepository.findAllByOrganisationAndDeletedAtIsNull(org)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
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
    public AssetDto assignToUser(UUID assetId, UUID userId) {
        Organisation org = requireTenantOrg();
        if (!hasAnyAuthority(ASSIGN_AUTHORITIES)) {
            throw new AccessDeniedException("You do not have permission to assign assets to users");
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
        Asset saved = assetRepository.save(asset);

        // Send asset-assignment email to the assigned user
        try {
            String now = LocalDateTime.now(ZoneOffset.UTC).format(EMAIL_DATE_FMT);
            Map<String, Object> model = new HashMap<>();
            model.put("firstName", user.getFirstName());
            model.put("eventTitle", "Asset Assigned to You");
            model.put("eventSubtitle", "An asset has been assigned to you in " + org.getName() + ".");
            model.put("eventType", "ASSIGNED");
            model.put("eventBadge", "\uD83D\uDCBC");
            model.put("accentColor", "#6366f1");
            model.put("assetName", saved.getName());
            model.put("assetTag", saved.getAssetTag());
            model.put("category", saved.getCategory() != null ? saved.getCategory().getName() : null);
            model.put("location", saved.getLocation() != null ? saved.getLocation().getName() : null);
            model.put("assignedTo", user.getFirstName() + " " + user.getLastName());
            model.put("eventDate", now);
            model.put("assetUrl", baseUrl + "/assets");
            emailService.sendTemplate(
                user.getEmail(),
                "Asset assigned: " + saved.getName(),
                "email/asset-lifecycle",
                model
            );
        } catch (Exception e) {
            log.warn("[EMAIL] Failed to send asset-assignment email: {}", e.getMessage());
        }

        return toDto(saved);
    }

    @Override
    @Transactional
    public AssetDto unassignUser(UUID assetId) {
        Organisation org = requireTenantOrg();
        if (!hasAnyAuthority(ASSIGN_AUTHORITIES)) {
            throw new AccessDeniedException("You do not have permission to unassign assets from users");
        }

        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found"));
        asset.setAssignedUser(null);
        // Assigning moved an in-stock asset to IN_USE; unassigning moves it back, unless an
        // active checkout is what keeps it in use.
        if (asset.getStatus() == AssetStatus.IN_USE
                && checkoutRecordRepository.findByAssetAndStatusAndDeletedAtIsNull(asset, CheckoutStatus.ACTIVE).isEmpty()) {
            asset.setStatus(AssetStatus.IN_STOCK);
        }
        return toDto(assetRepository.save(asset));
    }

    @Override
    @Transactional
    public AssetDto update(UUID id, AssetDto dto) {
        Organisation org = requireTenantOrg();
        if (!hasAnyAuthority(EDIT_AUTHORITIES)) {
            throw new AccessDeniedException("You do not have permission to edit assets");
        }
        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));
        Set<String> clears = validateClearFields(dto);

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
            asset.setCurrency(CurrencyResolver.normaliseIsoCode(dto.getCurrency()));
        if (dto.getDepreciationMethod() != null)
            asset.setDepreciationMethod(dto.getDepreciationMethod());
        if (dto.getUsefulLifeMonths() != null)
            asset.setUsefulLifeMonths(dto.getUsefulLifeMonths());
        if (dto.getResidualValue() != null)
            asset.setResidualValue(dto.getResidualValue());
        if (dto.getWarrantyExpiryDate() != null)
            asset.setWarrantyExpiryDate(dto.getWarrantyExpiryDate());
        requireEditableStatus(asset.getStatus(), dto.getStatus());
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
        if (dto.getInsurancePremiumPerYear() != null)
            asset.setInsurancePremiumPerYear(dto.getInsurancePremiumPerYear());
        if (dto.getDowntimeCostPerDay() != null)
            asset.setDowntimeCostPerDay(dto.getDowntimeCostPerDay());
        if (dto.getInsurancePolicyExpiry() != null)
            asset.setInsurancePolicyExpiry(dto.getInsurancePolicyExpiry());
        if (dto.getParentAssetId() != null)
            asset.setParentAsset(requireParentAsset(dto.getParentAssetId(), asset.getId(), org));

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

        clears.forEach(field -> CLEARABLE.get(field).clear().accept(asset));

        refreshBookValue(asset);
        Asset saved = assetRepository.save(asset);
        notificationService.notifyOrgAdmins(org, NotificationType.SYSTEM,
                "Asset Updated",
                AssetLabels.describe(saved) + " has been updated.",
                saved.getId(), "/api/v1/assets/" + saved.getId());
        return toDto(saved);
    }

    @Override
    @Transactional
    public AssetDto patch(UUID id, AssetDto dto) {
        return update(id, dto);
    }

    /** Loads a parent asset in the organisation, refusing the asset itself or one of its components. */
    private Asset requireParentAsset(UUID parentId, UUID selfId, Organisation org) {
        Asset parent = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(parentId, org)
                .orElseThrow(() -> new IllegalArgumentException("Parent asset not found in your organisation"));
        if (parent.getId().equals(selfId)) {
            throw new IllegalArgumentException("An asset cannot be its own parent");
        }
        HierarchyGuard.assertNotDescendant(parent, selfId, Asset::getParentAsset, Asset::getId,
                "An asset cannot be placed under one of its own components");
        return parent;
    }

    /**
     * DISPOSED is reached only through an approved disposal request (maker-checker,
     * sale value, write-off); setting it on create or edit bypassed all of that.
     * A disposed asset's status is final, so an edit cannot revive it either.
     *
     * <p>RETIRED stays settable here: there is no retirement workflow, and retiring
     * records no financial event (the asset stays on the register with its book value).
     */
    static void requireEditableStatus(AssetStatus current, AssetStatus requested) {
        if (requested == null || requested == current) return;
        if (requested == AssetStatus.DISPOSED) {
            throw new IllegalArgumentException(
                    "An asset is disposed through a disposal request, which needs approval; it cannot be set to DISPOSED directly");
        }
        if (current == AssetStatus.DISPOSED) {
            throw new IllegalArgumentException("A disposed asset's status cannot be changed");
        }
    }

    /**
     * Validates {@link AssetDto#getClearFields()}: every name must be clearable and
     * must not also carry a value in the same request.
     */
    private static Set<String> validateClearFields(AssetDto dto) {
        if (dto.getClearFields() == null || dto.getClearFields().isEmpty()) {
            return Set.of();
        }
        Set<String> clears = new HashSet<>(dto.getClearFields());
        for (String field : clears) {
            if (!CLEARABLE_FIELDS.contains(field)) {
                throw new IllegalArgumentException("Field cannot be cleared: " + field
                        + ". Clearable fields: " + new TreeSet<>(CLEARABLE_FIELDS));
            }
        }
        for (String field : clears) {
            if (CLEARABLE.get(field).requested().apply(dto) != null) {
                throw new IllegalArgumentException("Field is both set and cleared: " + field);
            }
        }
        return clears;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Organisation org = requireTenantOrg();
        if (!hasAnyAuthority(DELETE_AUTHORITIES)) {
            throw new AccessDeniedException("You do not have permission to delete assets");
        }
        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));
        String assetName = asset.getName();
        String assetTag = asset.getAssetTag();
        UUID assetId = asset.getId();
        asset.setDeletedAt(Instant.now());
        assetRepository.save(asset);
        notificationService.notifyOrgAdmins(org, NotificationType.SYSTEM,
                "Asset Deleted",
                AssetLabels.describe(assetName, assetTag) + " has been removed from the inventory.",
                assetId, "/api/v1/assets");
    }

    // ────────────────────────────────────────────────────
    // Filtered list queries
    // ────────────────────────────────────────────────────

    @Override
    @Transactional
    public Set<AssetDto> listByStatus(AssetStatus status) {
        Organisation org = requireTenantOrg();
        return assetRepository.findByOrganisationIdAndStatusAndDeletedAtIsNull(org.getId(), status)
                .stream().map(this::toDto).collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public Set<AssetDto> listByDepartment(UUID departmentId) {
        Organisation org = requireTenantOrg();
        // Ensure the department belongs to this tenant
        departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(departmentId, org)
                .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));
        return assetRepository.findByDepartmentIdAndDeletedAtIsNull(departmentId)
                .stream().map(this::toDto).collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public Set<AssetDto> listByCategory(UUID categoryId) {
        Organisation org = requireTenantOrg();
        // Ensure category belongs to this tenant
        categoryRepository.findByIdAndOrganisationAndDeletedAtIsNull(categoryId, org)
                .orElseThrow(() -> new IllegalArgumentException("Category not found in your organisation"));
        return assetRepository.findByOrganisationAndCategoryIdAndDeletedAtIsNull(org, categoryId)
                .stream().map(this::toDto).collect(Collectors.toSet());
    }

    private static final Set<String> SORTABLE_FIELDS = Set.of(
        "name", "assetTag", "serialNumber", "manufacturer", "model",
        "purchaseCost", "purchaseDate", "createdAt", "updatedAt", "status", "condition",
        "assetType", "warrantyExpiryDate", "currentBookValue"
    );

    @Override
    @Transactional
    public PagedResponseDto<AssetDto> listPaged(AssetFilterRequest req) {
        Organisation org = requireTenantOrg();

        int pageNum  = (req.page() != null && req.page()  >= 0) ? req.page()              : 0;
        int pageSize = (req.size() != null && req.size()  >  0) ? Math.min(req.size(), 100) : 20;

        Sort sort = resolveSort(req.sort());
        Pageable pageable = PageRequest.of(pageNum, pageSize, sort);

        Page<Asset> page = assetRepository.findAll(AssetSpecification.filtered(org, req), pageable);

        PagedResponseDto<AssetDto> response = new PagedResponseDto<>();
        response.setTotal(page.getTotalElements());
        response.setLimit(pageSize);
        response.setOffset((long) pageNum * pageSize);
        response.setItems(page.getContent().stream().map(this::toDto).collect(Collectors.toList()));
        return response;
    }

    private Sort resolveSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "name");
        }
        String[] parts = sortParam.split(",", 2);
        String field = parts[0].trim();
        Sort.Direction dir = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()))
            ? Sort.Direction.DESC : Sort.Direction.ASC;
        return SORTABLE_FIELDS.contains(field)
            ? Sort.by(dir, field)
            : Sort.by(Sort.Direction.ASC, "name");
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
        d.setWarrantyExpiryDate(a.getWarrantyExpiryDate());
        d.setStatus(a.getStatus());
        d.setCondition(a.getCondition());
        d.setInvoiceId(a.getInvoiceId());
        d.setInsurancePolicyId(a.getInsurancePolicyId());
        d.setProcurementType(a.getProcurementType());
        d.setCostCenter(a.getCostCenter());
        d.setInsurancePremiumPerYear(a.getInsurancePremiumPerYear());
        d.setDowntimeCostPerDay(a.getDowntimeCostPerDay());
        d.setInsurancePolicyExpiry(a.getInsurancePolicyExpiry());
        if (a.getParentAsset() != null)
            d.setParentAssetId(a.getParentAsset().getId());
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
        d.setCreatedAt(a.getCreatedAt());
        d.setUpdatedAt(a.getUpdatedAt());
        applyDepreciation(d, a);
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
                        e.getCreatedAt(),
                        e.getActorEmail(),
                        e.getMethod(),
                        e.getPath(),
                        e.getResponseStatus())));

        // 2. Transfers
        assetTransferRepository.findByAssetIdAndDeletedAtIsNull(assetId)
                .forEach(t -> timeline.add(AssetHistoryEventDto.ofTransfer(
                        t.getId(),
                        t.getCreatedAt(),
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
                        m.getCreatedAt(),
                        m.getMaintenanceType() != null ? m.getMaintenanceType().name() : null,
                        m.getStatus() != null ? m.getStatus().name() : null,
                        m.getScheduledDate(),
                        m.getPerformedDate())));

        // 4. Disposal records
        disposalRecordRepository.findByAssetIdAndDeletedAtIsNull(assetId).stream()
                .filter(com.assetiq.models.DisposalRecord::isEffective)
                .forEach(d -> timeline.add(AssetHistoryEventDto.ofDisposal(
                        d.getId(),
                        d.getCreatedAt(),
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
    @Transactional
    public TcoDto getTco(UUID assetId) {
        Organisation org = requireTenantOrg();
        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));

        // 1. Acquisition cost
        BigDecimal acquisitionCost = asset.getPurchaseCost() != null ? asset.getPurchaseCost() : BigDecimal.ZERO;

        // TCO is expressed in the asset's own currency. Maintenance costs and
        // disposal values carry their own currency, so each is converted into the
        // asset's currency; amounts without a rate are excluded and reported.
        CurrencyConversion fx = moneyAggregator.beginIn(org, asset.getCurrency());

        // 2. Maintenance costs
        Set<com.assetiq.models.MaintenanceRecord> maintenanceRecords =
                maintenanceRecordRepository.findByAssetIdAndDeletedAtIsNull(assetId);
        BigDecimal totalMaintenanceCost = fx.sum(maintenanceRecords,
                com.assetiq.models.MaintenanceRecord::getCost,
                com.assetiq.models.MaintenanceRecord::effectiveCurrency).amount();
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
        BigDecimal disposalRecovery = fx.sum(disposalRecordRepository.findByAssetIdAndDeletedAtIsNull(assetId)
                        .stream().filter(com.assetiq.models.DisposalRecord::isEffective).toList(),
                com.assetiq.models.DisposalRecord::getSaleValue,
                com.assetiq.models.DisposalRecord::effectiveCurrency).amount();

        // 6. Net TCO = acquisition + maintenance + insurance + downtime - recovery
        //
        // Currency: every component is in the asset's own currency - maintenance
        // and disposal amounts were converted above; insurance and downtime rates
        // are asset fields. The result is labelled with the asset's currency (not
        // the tenant base), and flags any amount excluded for lack of a rate.
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
        dto.setCurrency(asset.getCurrency() != null ? asset.getCurrency() : fx.baseCurrency());
        dto.setComplete(fx.isComplete());
        dto.setMissingRates(fx.missingRates());
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
        UUID assetId = com.assetiq.assets.AssetQrCodes.parse(payload)
                .orElseThrow(() -> new IllegalArgumentException("Invalid QR payload: " + payload));
        AssetDto result = get(assetId);
        if (result == null) {
            throw new IllegalArgumentException("Asset not found for QR payload: " + payload);
        }
        return result;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public AssetStatsDto getStats() {
        Organisation org = requireTenantOrg();

        AssetStatsDto stats = new AssetStatsDto();
        stats.setTotal(assetRepository.countByOrganisationAndDeletedAtIsNull(org));

        for (Object[] row : assetRepository.countGroupedByStatus(org)) {
            String statusName = row[0] == null ? "" : row[0].toString();
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            switch (statusName) {
                case "IN_USE"      -> stats.setInUse(count);
                case "IN_STOCK"    -> stats.setInStock(count);
                case "MAINTENANCE" -> stats.setMaintenance(count);
                case "RETIRED"     -> stats.setRetired(count);
                case "DISPOSED"    -> stats.setDisposed(count);
                case "RESERVED"    -> stats.setReserved(count);
                case "MISSING"     -> stats.setMissing(count);
                case "PENDING_PROCUREMENT" -> stats.setPendingProcurement(count);
                case "UNDER_REPAIR" -> stats.setUnderRepair(count);
                default            -> { /* ignore unknown statuses */ }
            }
        }

        long assigned = assetRepository.countAssigned(org);
        stats.setAssigned(assigned);
        stats.setUnassigned(stats.getTotal() - assigned);

        // Register-wide value (same basis as the dashboard's totalAssetValue).
        CurrencyConversion fx = moneyAggregator.begin(org);
        MoneyAccumulator value = fx.newAccumulator();
        for (Object[] row : assetRepository.sumOnBookPurchaseCostByCurrency(org)) {
            value.add((BigDecimal) row[1], (String) row[0]);
        }
        stats.setTotalValue(value.amount());
        stats.setCurrency(fx.baseCurrency());
        stats.setComplete(value.isComplete());
        stats.setMissingRates(value.missingRates());
        return stats;
    }
}
