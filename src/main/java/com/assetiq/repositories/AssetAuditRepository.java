package com.assetiq.repositories;

import com.assetiq.models.AssetAudit;
import com.assetiq.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface AssetAuditRepository extends JpaRepository<AssetAudit, UUID> {
    Set<AssetAudit> findByOrganisationId(UUID organisationId);

    Set<AssetAudit> findByDepartmentId(UUID departmentId);

    Set<AssetAudit> findByAuditDateBetween(LocalDate startDate, LocalDate endDate);

    Set<AssetAudit> findByConductedById(UUID userId);

    // Tenant + soft-delete scoped
    Optional<AssetAudit> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    Set<AssetAudit> findByOrganisationAndDeletedAtIsNull(Organisation organisation);

    Set<AssetAudit> findByDepartmentIdAndDeletedAtIsNull(UUID departmentId);

    Set<AssetAudit> findByOrganisationAndAuditDateBetweenAndDeletedAtIsNull(
            Organisation organisation, LocalDate startDate, LocalDate endDate);

    Set<AssetAudit> findByConductedByIdAndDeletedAtIsNull(UUID userId);
}
