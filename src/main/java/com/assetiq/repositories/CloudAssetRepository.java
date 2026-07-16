package com.assetiq.repositories;

import com.assetiq.enums.CloudProvider;
import com.assetiq.models.CloudAsset;
import com.assetiq.models.Organisation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CloudAssetRepository extends JpaRepository<CloudAsset, UUID> {

    Page<CloudAsset> findByOrganisationAndDeletedAtIsNull(Organisation organisation, Pageable pageable);

    List<CloudAsset> findByOrganisationAndDeletedAtIsNull(Organisation organisation);

    List<CloudAsset> findByOrganisationAndProviderAndDeletedAtIsNull(Organisation organisation, CloudProvider provider);

    Optional<CloudAsset> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    @Query("SELECT c FROM CloudAsset c WHERE c.organisation = :org AND c.deletedAt IS NULL " +
           "ORDER BY c.monthlyCostEstimate DESC")
    List<CloudAsset> findTopByOrganisationOrderByCost(@Param("org") Organisation organisation, Pageable pageable);

    /**
     * Look up a cloud asset by its cloud-native resource ID, provider, and tenant.
     * Used by the AWS sync to implement upsert (insert if not present, update if already known).
     */
    Optional<CloudAsset> findByResourceIdAndProviderAndOrganisationAndDeletedAtIsNull(
            String resourceId, CloudProvider provider, Organisation organisation);
}
