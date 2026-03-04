package com.example.demo.services;

import com.example.demo.dto.MaintenanceRecordDto;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public interface MaintenanceService {
    MaintenanceRecordDto createMaintenanceRecord(MaintenanceRecordDto recordDto);

    MaintenanceRecordDto getMaintenanceRecordById(UUID id);

    Set<MaintenanceRecordDto> getAllMaintenanceRecords();

    Set<MaintenanceRecordDto> getMaintenanceRecordsByAsset(UUID assetId);

    Set<MaintenanceRecordDto> getMaintenanceRecordsByVendor(UUID vendorId);

    Set<MaintenanceRecordDto> getMaintenanceRecordsDueBy(LocalDate date);

    MaintenanceRecordDto updateMaintenanceRecord(UUID id, MaintenanceRecordDto recordDto);

    MaintenanceRecordDto completeMaintenanceRecord(UUID id);

    void deleteMaintenanceRecord(UUID id);
}
