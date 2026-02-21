package com.example.demo.repositories;

import com.example.demo.enums.AssetStatus;
import com.example.demo.models.Asset;
import com.example.demo.models.Department;
import com.example.demo.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {
    List<Asset> findAllByDeletedAtIsNull();
    Optional<Asset> findByIdAndDeletedAtIsNull(UUID id);
    Optional<Asset> findByNameAndOrganisationAndDeletedAtIsNull(String name, Organisation organisation);

    Optional<Asset> findByNameIgnoreCaseAndDeletedAtIsNull(String name);
    boolean existsByNameIgnoreCaseAndDeletedAtIsNull(String name);

    boolean existsByNameIgnoreCaseAndOrganisationAndDepartmentAndDeletedAtIsNull(
            String name, Organisation organisation, Department department);

    // Organisation-scoped queries
    List<Asset> findAllByOrganisationAndDeletedAtIsNull(Organisation organisation);
    Optional<Asset> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);
    Optional<Asset> findByNameIgnoreCaseAndOrganisationAndDeletedAtIsNull(String name, Organisation organisation);

    // Asset-specific queries
    Optional<Asset> findByAssetTagAndDeletedAtIsNull(String assetTag);
    Optional<Asset> findBySerialNumberAndDeletedAtIsNull(String serialNumber);
    Set<Asset> findByDepartmentIdAndDeletedAtIsNull(UUID departmentId);
    Set<Asset> findByLocationIdAndDeletedAtIsNull(UUID locationId);
    Set<Asset> findByAssignedUserIdAndDeletedAtIsNull(UUID userId);
    Set<Asset> findByStatusAndDeletedAtIsNull(AssetStatus status);
    Set<Asset> findByOrganisationIdAndStatusAndDeletedAtIsNull(UUID organisationId, AssetStatus status);
    Set<Asset> findByCategoryIdAndDeletedAtIsNull(UUID categoryId);
}
