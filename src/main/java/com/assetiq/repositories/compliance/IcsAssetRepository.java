package com.assetiq.repositories.compliance;

import com.assetiq.models.Organisation;
import com.assetiq.models.compliance.IcsAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IcsAssetRepository extends JpaRepository<IcsAsset, UUID> {

    List<IcsAsset> findByOrganisationAndDeletedAtIsNull(Organisation organisation);

    Optional<IcsAsset> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    Optional<IcsAsset> findByAssetIdAndDeletedAtIsNull(UUID assetId);
}
