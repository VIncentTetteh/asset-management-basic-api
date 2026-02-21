package com.example.demo.repositories;

import com.example.demo.models.DisposalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Repository
public interface DisposalRecordRepository extends JpaRepository<DisposalRecord, UUID> {
    Set<DisposalRecord> findByAssetId(UUID assetId);
    Set<DisposalRecord> findByOrganisationId(UUID organisationId);
    Set<DisposalRecord> findByDisposalDateBetween(LocalDate startDate, LocalDate endDate);
    Set<DisposalRecord> findByApprovedById(UUID userId);
}

