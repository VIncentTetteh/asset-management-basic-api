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

    /**
     * Open maintenance falling due on or before {@code cutoff}, including work
     * already overdue, as a slim due-row.
     *
     * <p>Cost: an index range scan on {@code next_due_date} within the tenant,
     * joined to {@code asset} on its primary key. Nothing is loaded as an entity,
     * so the asset name costs no extra round trip.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT new com.assetiq.services.insights.DueRow("
            + "r.id, a.name, r.description, r.nextDueDate, r.cost, null, r.currency, a.id, a.name) "
            + "FROM MaintenanceRecord r LEFT JOIN r.asset a "
            + "WHERE r.organisation = :org AND r.deletedAt IS NULL "
            + "AND r.nextDueDate IS NOT NULL AND r.nextDueDate <= :cutoff "
            + "AND r.status NOT IN (com.assetiq.enums.MaintenanceStatus.COMPLETED, "
            + "com.assetiq.enums.MaintenanceStatus.CANCELLED) "
            + "ORDER BY r.nextDueDate ASC")
    java.util.List<com.assetiq.services.insights.DueRow> findDueBy(
            @org.springframework.data.repository.query.Param("org") Organisation org,
            @org.springframework.data.repository.query.Param("cutoff") LocalDate cutoff);

    /** How many open maintenance jobs are past their due date, whatever the reporting period. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(r) FROM MaintenanceRecord r WHERE r.organisation = :org AND r.deletedAt IS NULL "
            + "AND r.nextDueDate IS NOT NULL AND r.nextDueDate < :today "
            + "AND r.status NOT IN (com.assetiq.enums.MaintenanceStatus.COMPLETED, "
            + "com.assetiq.enums.MaintenanceStatus.CANCELLED)")
    long countOverdue(@org.springframework.data.repository.query.Param("org") Organisation org,
                      @org.springframework.data.repository.query.Param("today") LocalDate today);

    /**
     * The jobs {@link #countOverdue} counts, oldest due first, as slim rows for
     * the mobile Home queue. Page with {@code PageRequest.of(0, n)}. The asset is
     * left-joined so a row the count includes can never drop out of the list.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT new com.assetiq.services.mobile.MaintenanceQueueRow("
            + "r.id, a.id, a.name, r.maintenanceType, r.status, r.nextDueDate) "
            + "FROM MaintenanceRecord r LEFT JOIN r.asset a "
            + "WHERE r.organisation = :org AND r.deletedAt IS NULL "
            + "AND r.nextDueDate IS NOT NULL AND r.nextDueDate < :today "
            + "AND r.status NOT IN (com.assetiq.enums.MaintenanceStatus.COMPLETED, "
            + "com.assetiq.enums.MaintenanceStatus.CANCELLED) "
            + "ORDER BY r.nextDueDate ASC, r.id")
    java.util.List<com.assetiq.services.mobile.MaintenanceQueueRow> findOverdueQueue(
            @org.springframework.data.repository.query.Param("org") Organisation org,
            @org.springframework.data.repository.query.Param("today") LocalDate today,
            org.springframework.data.domain.Pageable page);

    /** Distinct assets with maintenance open and past due, whatever the reporting period. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(DISTINCT r.asset.id) FROM MaintenanceRecord r "
            + "WHERE r.organisation = :org AND r.deletedAt IS NULL AND r.asset IS NOT NULL "
            + "AND r.nextDueDate IS NOT NULL AND r.nextDueDate <= :today "
            + "AND r.status NOT IN (com.assetiq.enums.MaintenanceStatus.COMPLETED, "
            + "com.assetiq.enums.MaintenanceStatus.CANCELLED)")
    long countAssetsNeedingMaintenance(@org.springframework.data.repository.query.Param("org") Organisation org,
                                       @org.springframework.data.repository.query.Param("today") LocalDate today);

}
