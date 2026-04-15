package com.assetiq.repositories;

import com.assetiq.models.OrganisationStorageConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganisationStorageConfigRepository extends JpaRepository<OrganisationStorageConfig, UUID> {

    Optional<OrganisationStorageConfig> findByOrganisationIdAndDeletedAtIsNull(UUID organisationId);

    boolean existsByOrganisationIdAndDeletedAtIsNull(UUID organisationId);
}
