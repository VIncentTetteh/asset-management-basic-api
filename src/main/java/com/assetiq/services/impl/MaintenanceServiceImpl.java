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

        MaintenanceRecord record = new MaintenanceRecord();
        record.setAsset(asset);
        record.setMaintenanceType(recordDto.getMaintenanceType());
        record.setDescription(recordDto.getDescription());
        record.setScheduledDate(recordDto.getScheduledDate());
        record.setPerformedDate(recordDto.getPerformedDate());
        record.setCost(recordDto.getCost());
        record.setStatus(recordDto.getStatus() != null ? recordDto.getStatus() : MaintenanceStatus.SCHEDULED);
        record.setNextDueDate(recordDto.getNextDueDate());

        if (recordDto.getVendorId() != null) {
            record.setVendor(supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(recordDto.getVendorId(), org)
                    .orElseThrow(() -> new IllegalArgumentException("Vendor not found in your organisation")));
        }

        record.setOrganisation(org);
        MaintenanceRecord savedRecord = recordRepository.save(record);

        // M6: update asset status to MAINTENANCE when record is created
        asset.setStatus(AssetStatus.MAINTENANCE);
        assetRepository.save(asset);

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

        record.setMaintenanceType(recordDto.getMaintenanceType());
        record.setDescription(recordDto.getDescription());
        record.setScheduledDate(recordDto.getScheduledDate());
        record.setPerformedDate(recordDto.getPerformedDate());
        record.setCost(recordDto.getCost());
        record.setStatus(recordDto.getStatus());
        record.setNextDueDate(recordDto.getNextDueDate());

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
        if (recordDto.getStatus() != null) {
            record.setStatus(recordDto.getStatus());
        }
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

        record.setStatus(MaintenanceStatus.COMPLETED);
        record.setPerformedDate(LocalDate.now());
        recordRepository.save(record);

        // M6: update asset status back to IN_USE when maintenance is completed
        Asset asset = record.getAsset();
        asset.setStatus(AssetStatus.IN_USE);
        assetRepository.save(asset);

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
        dto.setStatus(record.getStatus());
        dto.setNextDueDate(record.getNextDueDate());
        return dto;
    }
}
