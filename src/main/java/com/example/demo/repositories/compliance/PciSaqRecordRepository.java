package com.example.demo.repositories.compliance;

import com.example.demo.models.Organisation;
import com.example.demo.models.compliance.PciSaqRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PciSaqRecordRepository extends JpaRepository<PciSaqRecord, UUID> {

    List<PciSaqRecord> findByOrganisationAndDeletedAtIsNullOrderByRequirementNumber(Organisation organisation);

    long countByOrganisationAndComplianceStatusAndDeletedAtIsNull(
            Organisation organisation, PciSaqRecord.ComplianceAnswer status);

    Optional<PciSaqRecord> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);
}
