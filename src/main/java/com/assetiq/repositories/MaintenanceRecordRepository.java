package com.assetiq.repositories;

import com.assetiq.models.MaintenanceRecord;
import com.assetiq.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, UUID> {
    Set<MaintenanceRecord> findByAssetId(UUID assetId);

    Set<MaintenanceRecord> findByAssetIdOrderByPerformedDateDesc(UUID assetId);

    Set<MaintenanceRecord> findByNextDueDateBefore(LocalDate date);

    Set<MaintenanceRecord> findByVendorId(UUID vendorId);

    // Soft-delete scoped
    Optional<MaintenanceRecord> findByIdAndDeletedAtIsNull(UUID id);

    Set<MaintenanceRecord> findByAssetIdAndDeletedAtIsNull(UUID assetId);

    Set<MaintenanceRecord> findByVendorIdAndDeletedAtIsNull(UUID vendorId);

    Set<MaintenanceRecord> findByNextDueDateBeforeAndDeletedAtIsNull(LocalDate date);

    // Organisation-scoped
    Set<MaintenanceRecord> findByOrganisationAndDeletedAtIsNull(Organisation organisation);

    Set<MaintenanceRecord> findByOrganisationAndNextDueDateBeforeAndDeletedAtIsNull(
            Organisation organisation, LocalDate date);
}
