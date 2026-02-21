package com.example.demo.repositories;

import com.example.demo.models.MaintenanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Repository
public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, UUID> {
    Set<MaintenanceRecord> findByAssetId(UUID assetId);
    Set<MaintenanceRecord> findByAssetIdOrderByPerformedDateDesc(UUID assetId);
    Set<MaintenanceRecord> findByNextDueDateBefore(LocalDate date);
    Set<MaintenanceRecord> findByVendorId(UUID vendorId);
}

