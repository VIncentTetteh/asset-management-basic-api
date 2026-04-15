package com.assetiq.repositories.compliance;

import com.assetiq.models.Organisation;
import com.assetiq.models.compliance.PciSaqRecord;
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
