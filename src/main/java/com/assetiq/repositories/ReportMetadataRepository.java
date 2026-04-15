package com.assetiq.repositories;

import com.assetiq.models.ReportMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReportMetadataRepository extends JpaRepository<ReportMetadata, UUID> {
    Optional<ReportMetadata> findByIdAndOrganisationIdAndDeletedAtIsNull(UUID id, UUID organisationId);

    Page<ReportMetadata> findByOrganisationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID organisationId, Pageable pageable);
}

