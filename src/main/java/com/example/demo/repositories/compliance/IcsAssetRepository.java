package com.example.demo.repositories.compliance;

import com.example.demo.models.Organisation;
import com.example.demo.models.compliance.IcsAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IcsAssetRepository extends JpaRepository<IcsAsset, UUID> {

    List<IcsAsset> findByOrganisationAndDeletedAtIsNull(Organisation organisation);

    Optional<IcsAsset> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    Optional<IcsAsset> findByAssetIdAndDeletedAtIsNull(UUID assetId);
}
