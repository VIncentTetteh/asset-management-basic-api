package com.example.demo.repositories;

import com.example.demo.models.AssetTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.UUID;

@Repository
public interface AssetTransferRepository extends JpaRepository<AssetTransfer, UUID> {
    Set<AssetTransfer> findByAssetId(UUID assetId);
    Set<AssetTransfer> findByFromDepartmentId(UUID departmentId);
    Set<AssetTransfer> findByToDepartmentId(UUID departmentId);
    Set<AssetTransfer> findByRequestedById(UUID userId);
}

