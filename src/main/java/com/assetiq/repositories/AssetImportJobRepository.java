package com.assetiq.repositories;

import com.assetiq.enums.ImportJobStatus;
import com.assetiq.models.AssetImportJob;
import com.assetiq.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssetImportJobRepository extends JpaRepository<AssetImportJob, UUID> {
    Optional<AssetImportJob> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    // Useful for later scheduled retry/cancel implementations.
    Optional<AssetImportJob> findFirstByOrganisationAndStatusAndDeletedAtIsNull(Organisation organisation, ImportJobStatus status);
}

