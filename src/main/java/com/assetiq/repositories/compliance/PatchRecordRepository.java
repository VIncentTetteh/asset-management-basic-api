package com.assetiq.repositories.compliance;

import com.assetiq.models.Asset;
import com.assetiq.models.Organisation;
import com.assetiq.models.compliance.PatchRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PatchRecordRepository extends JpaRepository<PatchRecord, UUID> {

    Page<PatchRecord> findByOrganisationAndDeletedAtIsNullOrderByAppliedAtDesc(
            Organisation organisation, Pageable pageable);

    Page<PatchRecord> findByOrganisationAndAssetAndDeletedAtIsNullOrderByAppliedAtDesc(
            Organisation organisation, Asset asset, Pageable pageable);

    Optional<PatchRecord> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);
}
