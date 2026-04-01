package com.example.demo.repositories;

import com.example.demo.enums.ImportJobStatus;
import com.example.demo.models.AssetImportJob;
import com.example.demo.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssetImportJobRepository extends JpaRepository<AssetImportJob, UUID> {
    Optional<AssetImportJob> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    // Useful for later scheduled retry/cancel implementations.
    Optional<AssetImportJob> findFirstByOrganisationAndStatusAndDeletedAtIsNull(Organisation organisation, ImportJobStatus status);
}

