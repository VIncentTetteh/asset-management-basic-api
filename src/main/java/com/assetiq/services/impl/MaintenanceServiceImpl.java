package com.assetiq.services.impl;

import com.assetiq.dto.MaintenanceRecordDto;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.MaintenanceStatus;
import com.assetiq.models.MaintenanceRecord;
import com.assetiq.models.Asset;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.MaintenanceRecordRepository;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.SupplierRepository;
import com.assetiq.enums.NotificationType;
import com.assetiq.services.CurrencyResolver;
import com.assetiq.services.MaintenanceService;
import com.assetiq.services.NotificationService;
import com.assetiq.services.TenantAwareService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class MaintenanceServiceImpl extends TenantAwareService implements MaintenanceService {

    private final MaintenanceRecordRepository recordRepository;
    private final AssetRepository assetRepository;
    private final SupplierRepository supplierRepository;
    private final NotificationService notificationService;

    public MaintenanceServiceImpl(MaintenanceRecordRepository recordRepository,
            AssetRepository assetRepository,
            SupplierRepository supplierRepository,
            OrganisationRepository organisationRepository,
            NotificationService notificationService) {
        super(organisationRepository);
        this.recordRepository = recordRepository;
        this.assetRepository = assetRepository;
        this.supplierRepository = supplierRepository;
        this.notificationService = notificationService;
    }

    @Override
    public MaintenanceRecordDto createMaintenanceRecord(MaintenanceRecordDto recordDto) {
        Organisation org = requireTenantOrg();

        // Asset must belong to the tenant org
        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(recordDto.getAssetId(), org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found in your organisation"));
        if (asset.getStatus() == AssetStatus.DISPOSED || asset.getStatus() == AssetStatus.RETIRED) {
            throw new IllegalStateException("Asset '" + asset.getName() + "' is " + asset.getStatus()
                    + "; maintenance cannot be scheduled for it.");
        }

        MaintenanceRecord record = new MaintenanceRecord();
        record.setAsset(asset);
        record.setMaintenanceType(recordDto.getMaintenanceType());
        record.setDescription(recordDto.getDescription());
        record.setScheduledDate(recordDto.getScheduledDate());
        record.setPerformedDate(recordDto.getPerformedDate());
        record.setCost(recordDto.getCost());
        record.setCurrency(recordCurrency(recordDto.getCurrency(), asset));
        record.setStatus(recordDto.getStatus() != null ? recordDto.getStatus() : MaintenanceStatus.SCHEDULED);
        // Work logged as already done was performed today unless the request says when.
        if (record.getStatus() == MaintenanceStatus.COMPLETED && record.getPerformedDate() == null) {
            record.setPerformedDate(LocalDate.now());
        }
        record.setNextDueDate(recordDto.getNextDueDate());

        if (recordDto.getVendorId() != null) {
            record.setVendor(supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(recordDto.getVendorId(), org)
                    .orElseThrow(() -> new IllegalArgumentException("Vendor not found in your organisation")));
        }

        record.setOrganisation(org);
        MaintenanceRecord savedRecord = recordRepository.save(record);

        // M6: an open record takes the asset into MAINTENANCE. A record logged as
        // already COMPLETED/CANCELLED (history) leaves the asset alone.
        if (isOpen(savedRecord.getStatus())) {
            asset.setStatus(AssetStatus.MAINTENANCE);
            assetRepository.save(asset);
        }

        notificationService.notifyOrgAdmins(org, NotificationType.MAINTENANCE,
                "Maintenance Record Created",
                "A maintenance record has been created for asset '" + asset.getName() + "' ("
                        + record.getMaintenanceType() + ").",
                savedRecord.getId(), "/api/v1/maintenance/" + savedRecord.getId());
        return mapToDto(savedRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public MaintenanceRecordDto getMaintenanceRecordById(UUID id) {
        Organisation org = requireTenantOrg();
        MaintenanceRecord record = recordRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Maintenance record not found"));
        if (!record.getOrganisation().getId().equals(org.getId())) {
            throw new IllegalArgumentException("Maintenance record not found");
        }
        return mapToDto(record);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<MaintenanceRecordDto> getAllMaintenanceRecords() {
        Organisation org = requireTenantOrg();
        return recordRepository.findByOrganisationAndDeletedAtIsNull(org).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<MaintenanceRecordDto> getMaintenanceRecordsByAsset(UUID assetId) {
        Organisation org = requireTenantOrg();
        // Verify asset belongs to tenant
        assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found in your organisation"));
        return recordRepository.findByAssetIdAndDeletedAtIsNull(assetId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<MaintenanceRecordDto> getMaintenanceRecordsByVendor(UUID vendorId) {
        Organisation org = requireTenantOrg();
        // Verify vendor belongs to tenant
        if (!supplierRepository.existsByIdAndOrganisation(vendorId, org)) {
            throw new IllegalArgumentException("Vendor not found in your organisation");
        }
        return recordRepository.findByVendorIdAndDeletedAtIsNull(vendorId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<MaintenanceRecordDto> getMaintenanceRecordsDueBy(LocalDate date) {
        Organisation org = requireTenantOrg();
        return recordRepository.findByOrganisationAndNextDueDateBeforeAndDeletedAtIsNull(org, date).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    public MaintenanceRecordDto updateMaintenanceRecord(UUID id, MaintenanceRecordDto recordDto) {
        Organisation org = requireTenantOrg();
        MaintenanceRecord record = recordRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Maintenance record not found"));
        if (!record.getAsset().getOrganisation().getId().equals(org.getId())) {
            throw new IllegalArgumentException("Maintenance record not found");
        }

        MaintenanceStatus before = record.getStatus();
        // PUT replaces every editable field, so an omitted optional field is cleared
        // (vendor included). Status is the exception: omitted means unchanged.
        record.setMaintenanceType(recordDto.getMaintenanceType());
        record.setDescription(recordDto.getDescription());
        record.setScheduledDate(recordDto.getScheduledDate());
        record.setPerformedDate(recordDto.getPerformedDate());
        record.setCost(recordDto.getCost());
        record.setCurrency(recordCurrency(recordDto.getCurrency(), record.getAsset()));
        if (recordDto.getStatus() != null) {
            record.setStatus(recordDto.getStatus());
        }
        record.setNextDueDate(recordDto.getNextDueDate());
        record.setVendor(recordDto.getVendorId() == null ? null
                : supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(recordDto.getVendorId(), org)
                        .orElseThrow(() -> new IllegalArgumentException("Vendor not found in your organisation")));
        onStatusChange(record, before);

        MaintenanceRecord updatedRecord = recordRepository.save(record);
        return mapToDto(updatedRecord);
    }

    @Override
    public MaintenanceRecordDto patchMaintenanceRecord(UUID id, MaintenanceRecordDto recordDto) {
        Organisation org = requireTenantOrg();
        MaintenanceRecord record = recordRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Maintenance record not found"));
        if (!record.getAsset().getOrganisation().getId().equals(org.getId())) {
            throw new IllegalArgumentException("Maintenance record not found");
        }

        if (recordDto.getMaintenanceType() != null) {
            record.setMaintenanceType(recordDto.getMaintenanceType());
        }
        if (recordDto.getDescription() != null) {
            record.setDescription(recordDto.getDescription());
        }
        if (recordDto.getScheduledDate() != null) {
            record.setScheduledDate(recordDto.getScheduledDate());
        }
        if (recordDto.getPerformedDate() != null) {
            record.setPerformedDate(recordDto.getPerformedDate());
        }
        if (recordDto.getCost() != null) {
            record.setCost(recordDto.getCost());
        }
        if (recordDto.getCurrency() != null) {
            record.setCurrency(CurrencyResolver.normaliseIsoCode(recordDto.getCurrency()));
        }
        MaintenanceStatus before = record.getStatus();
        if (recordDto.getStatus() != null) {
            record.setStatus(recordDto.getStatus());
        }
        onStatusChange(record, before);
        if (recordDto.getNextDueDate() != null) {
            record.setNextDueDate(recordDto.getNextDueDate());
        }
        if (recordDto.getVendorId() != null) {
            record.setVendor(supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(recordDto.getVendorId(), org)
                    .orElseThrow(() -> new IllegalArgumentException("Vendor not found in your organisation")));
        }

        MaintenanceRecord updatedRecord = recordRepository.save(record);
        return mapToDto(updatedRecord);
    }

    @Override
    public MaintenanceRecordDto completeMaintenanceRecord(UUID id) {
        Organisation org = requireTenantOrg();
        MaintenanceRecord record = recordRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Maintenance record not found"));
        if (!record.getAsset().getOrganisation().getId().equals(org.getId())) {
            throw new IllegalArgumentException("Maintenance record not found");
        }

        if (!isOpen(record.getStatus())) {
            throw new IllegalStateException("Only scheduled or in-progress maintenance can be completed (this one is "
                    + record.getStatus() + ").");
        }
        MaintenanceStatus before = record.getStatus();
        record.setStatus(MaintenanceStatus.COMPLETED);
        if (record.getPerformedDate() == null) {
            record.setPerformedDate(LocalDate.now());
        }
        onStatusChange(record, before);
        recordRepository.save(record);

        return mapToDto(record);
    }

    @Override
    public void deleteMaintenanceRecord(UUID id) {
        Organisation org = requireTenantOrg();
        MaintenanceRecord record = recordRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Maintenance record not found"));
        if (!record.getAsset().getOrganisation().getId().equals(org.getId())) {
            throw new IllegalArgumentException("Maintenance record not found");
        }
        record.setDeletedAt(Instant.now());
        recordRepository.save(record);
        // Deleting an open ticket must not strand the asset in MAINTENANCE.
        if (isOpen(record.getStatus())) {
            releaseAssetIfNoOpenWork(record);
        }
    }

    static boolean isOpen(MaintenanceStatus status) {
        return status == null || status == MaintenanceStatus.SCHEDULED || status == MaintenanceStatus.IN_PROGRESS;
    }

    /**
     * Keeps the asset's status in step with its maintenance: closing (completing or
     * cancelling) the last open record returns the asset to service; re-opening a
     * record puts it back into MAINTENANCE. Previously only the /complete endpoint
     * did this, and it always forced IN_USE — even for an unassigned asset.
     */
    private void onStatusChange(MaintenanceRecord record, MaintenanceStatus before) {
        boolean wasOpen = isOpen(before);
        boolean nowOpen = isOpen(record.getStatus());
        if (wasOpen && !nowOpen) {
            if (record.getStatus() == MaintenanceStatus.COMPLETED && record.getPerformedDate() == null) {
                record.setPerformedDate(LocalDate.now());
            }
            releaseAssetIfNoOpenWork(record);
        } else if (!wasOpen && nowOpen) {
            Asset asset = record.getAsset();
            if (asset.getStatus() != AssetStatus.DISPOSED && asset.getStatus() != AssetStatus.RETIRED) {
                asset.setStatus(AssetStatus.MAINTENANCE);
                assetRepository.save(asset);
            }
        }
    }

    private void releaseAssetIfNoOpenWork(MaintenanceRecord closed) {
        Asset asset = closed.getAsset();
        if (asset.getStatus() != AssetStatus.MAINTENANCE) return;
        boolean otherOpen = recordRepository.findByAssetIdAndDeletedAtIsNull(asset.getId()).stream()
                .anyMatch(r -> !r.getId().equals(closed.getId()) && isOpen(r.getStatus()));
        if (otherOpen) return;
        asset.setStatus(asset.getAssignedUser() != null ? AssetStatus.IN_USE : AssetStatus.IN_STOCK);
        assetRepository.save(asset);
    }

    /**
     * The currency to store for a maintenance cost or disposal value: the supplied
     * ISO code when given (validated), otherwise the asset's own currency.
     */
    static String recordCurrency(String supplied, Asset asset) {
        if (supplied != null && !supplied.isBlank()) {
            return CurrencyResolver.normaliseIsoCode(supplied);
        }
        return asset != null ? asset.getCurrency() : null;
    }

    private MaintenanceRecordDto mapToDto(MaintenanceRecord record) {
        MaintenanceRecordDto dto = new MaintenanceRecordDto();
        dto.setId(record.getId());
        dto.setAssetId(record.getAsset().getId());
        dto.setMaintenanceType(record.getMaintenanceType());
        dto.setDescription(record.getDescription());
        dto.setScheduledDate(record.getScheduledDate());
        dto.setPerformedDate(record.getPerformedDate());
        if (record.getVendor() != null) {
            dto.setVendorId(record.getVendor().getId());
        }
        dto.setCost(record.getCost());
        dto.setCurrency(record.effectiveCurrency());
        dto.setStatus(record.getStatus());
        dto.setNextDueDate(record.getNextDueDate());
        return dto;
    }
}
