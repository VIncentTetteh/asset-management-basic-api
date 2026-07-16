package com.assetiq.dpa.repository;

import com.assetiq.dpa.model.DsarRequest;
import com.assetiq.models.Organisation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DsarRequestRepository extends JpaRepository<DsarRequest, UUID> {

    Page<DsarRequest> findByOrganisationAndDeletedAtIsNull(Organisation org, Pageable pageable);

    Page<DsarRequest> findByOrganisationAndStatusAndDeletedAtIsNull(
            Organisation org, DsarRequest.Status status, Pageable pageable);

    Optional<DsarRequest> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation org);
}
