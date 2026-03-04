package com.example.demo.repositories;

import com.example.demo.models.DisposalRecord;
import com.example.demo.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface DisposalRecordRepository extends JpaRepository<DisposalRecord, UUID> {
    Set<DisposalRecord> findByAssetId(UUID assetId);

    Set<DisposalRecord> findByOrganisationId(UUID organisationId);

    Set<DisposalRecord> findByDisposalDateBetween(LocalDate startDate, LocalDate endDate);

    Set<DisposalRecord> findByApprovedById(UUID userId);

    // Tenant + soft-delete scoped
    Optional<DisposalRecord> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    Set<DisposalRecord> findByAssetIdAndDeletedAtIsNull(UUID assetId);

    Set<DisposalRecord> findByOrganisationAndDeletedAtIsNull(Organisation organisation);

    Set<DisposalRecord> findByOrganisationAndDisposalDateBetweenAndDeletedAtIsNull(
            Organisation organisation, LocalDate startDate, LocalDate endDate);

    Set<DisposalRecord> findByApprovedByIdAndDeletedAtIsNull(UUID userId);
}
