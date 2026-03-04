package com.example.demo.repositories;

import com.example.demo.models.AssetTransfer;
import com.example.demo.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface AssetTransferRepository extends JpaRepository<AssetTransfer, UUID> {
    Set<AssetTransfer> findByAssetId(UUID assetId);

    Set<AssetTransfer> findByFromDepartmentId(UUID departmentId);

    Set<AssetTransfer> findByToDepartmentId(UUID departmentId);

    Set<AssetTransfer> findByRequestedById(UUID userId);

    // Soft-delete scoped
    Optional<AssetTransfer> findByIdAndDeletedAtIsNull(UUID id);

    Set<AssetTransfer> findByAssetIdAndDeletedAtIsNull(UUID assetId);

    Set<AssetTransfer> findByFromDepartmentIdAndDeletedAtIsNull(UUID departmentId);

    Set<AssetTransfer> findByToDepartmentIdAndDeletedAtIsNull(UUID departmentId);

    Set<AssetTransfer> findByRequestedByIdAndDeletedAtIsNull(UUID userId);

    // Organisation-scoped
    Set<AssetTransfer> findByOrganisationAndDeletedAtIsNull(Organisation organisation);
}
