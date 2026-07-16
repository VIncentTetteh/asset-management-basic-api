package com.assetiq.repositories;

import com.assetiq.models.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** P0-8: JPA access to global {@link FeatureFlag} rows keyed by {@code key}. */
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {
    Optional<FeatureFlag> findByKey(String key);
}
