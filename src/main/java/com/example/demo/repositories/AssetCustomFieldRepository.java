package com.example.demo.repositories;

import com.example.demo.models.AssetCustomField;
import com.example.demo.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetCustomFieldRepository extends JpaRepository<AssetCustomField, UUID> {

    List<AssetCustomField> findByAssetIdAndDeletedAtIsNull(UUID assetId);

    Optional<AssetCustomField> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    Optional<AssetCustomField> findByAssetIdAndFieldNameAndDeletedAtIsNull(UUID assetId, String fieldName);

    boolean existsByAssetIdAndFieldNameAndDeletedAtIsNull(UUID assetId, String fieldName);
}
