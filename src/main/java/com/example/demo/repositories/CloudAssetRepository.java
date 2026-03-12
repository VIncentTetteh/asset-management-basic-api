package com.example.demo.repositories;

import com.example.demo.enums.CloudProvider;
import com.example.demo.models.CloudAsset;
import com.example.demo.models.Organisation;
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
}
