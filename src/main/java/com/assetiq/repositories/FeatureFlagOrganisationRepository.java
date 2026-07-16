package com.assetiq.repositories;

import com.assetiq.models.FeatureFlagOrganisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** P0-8: JPA access to per-organisation overrides for feature flags. */
public interface FeatureFlagOrganisationRepository
        extends JpaRepository<FeatureFlagOrganisation, UUID> {

    Optional<FeatureFlagOrganisation> findByFeatureFlagKeyAndOrganisationId(
            String featureFlagKey, UUID organisationId);
}
