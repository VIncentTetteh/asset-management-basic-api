package com.example.demo.services.impl;

import com.example.demo.dto.MaintenanceRecordDto;
import com.example.demo.enums.MaintenanceStatus;
import com.example.demo.models.MaintenanceRecord;
import com.example.demo.models.Asset;
import com.example.demo.repositories.MaintenanceRecordRepository;
import com.example.demo.repositories.AssetRepository;
import com.example.demo.repositories.SupplierRepository;
import com.example.demo.services.MaintenanceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class MaintenanceServiceImpl implements MaintenanceService {

    private final MaintenanceRecordRepository recordRepository;
    private final AssetRepository assetRepository;
    private final SupplierRepository supplierRepository;

    public MaintenanceServiceImpl(MaintenanceRecordRepository recordRepository,
                                AssetRepository assetRepository,
                                SupplierRepository supplierRepository) {
        this.recordRepository = recordRepository;
        this.assetRepository = assetRepository;
        this.supplierRepository = supplierRepository;
    }

    @Override
    public MaintenanceRecordDto createMaintenanceRecord(MaintenanceRecordDto recordDto) {
        Asset asset = assetRepository.findById(recordDto.getAssetId())
            .orElseThrow(() -> new IllegalArgumentException("Asset not found"));

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
            record.setVendor(supplierRepository.findById(recordDto.getVendorId())
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found")));
        }

        MaintenanceRecord savedRecord = recordRepository.save(record);
        return mapToDto(savedRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public MaintenanceRecordDto getMaintenanceRecordById(UUID id) {
        MaintenanceRecord record = recordRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Maintenance record not found"));
        return mapToDto(record);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<MaintenanceRecordDto> getMaintenanceRecordsByAsset(UUID assetId) {
        return recordRepository.findByAssetIdOrderByPerformedDateDesc(assetId).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<MaintenanceRecordDto> getMaintenanceRecordsByVendor(UUID vendorId) {
        return recordRepository.findByVendorId(vendorId).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<MaintenanceRecordDto> getMaintenanceRecordsDueBy(LocalDate date) {
        return recordRepository.findByNextDueDateBefore(date).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    public MaintenanceRecordDto updateMaintenanceRecord(UUID id, MaintenanceRecordDto recordDto) {
        MaintenanceRecord record = recordRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Maintenance record not found"));

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
    public MaintenanceRecordDto completeMaintenanceRecord(UUID id) {
        MaintenanceRecord record = recordRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Maintenance record not found"));

        record.setStatus(MaintenanceStatus.COMPLETED);
        record.setPerformedDate(LocalDate.now());

        MaintenanceRecord updatedRecord = recordRepository.save(record);
        return mapToDto(updatedRecord);
    }

    @Override
    public void deleteMaintenanceRecord(UUID id) {
        recordRepository.deleteById(id);
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

