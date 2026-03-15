package com.example.demo.repositories;

import com.example.demo.enums.AssetStatus;
import com.example.demo.models.Asset;
import com.example.demo.models.Department;
import com.example.demo.models.Organisation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {
        List<Asset> findAllByDeletedAtIsNull();

        List<Asset> findAllByCreatedByAndDeletedAtIsNull(String createdBy);

        Optional<Asset> findByIdAndDeletedAtIsNull(UUID id);

        Optional<Asset> findByNameAndOrganisationAndDeletedAtIsNull(String name, Organisation organisation);

        Optional<Asset> findByNameIgnoreCaseAndDeletedAtIsNull(String name);

        boolean existsByNameIgnoreCaseAndDeletedAtIsNull(String name);

        boolean existsByNameIgnoreCaseAndOrganisationAndDepartmentAndDeletedAtIsNull(
                        String name, Organisation organisation, Department department);

        boolean existsByNameIgnoreCaseAndOrganisationAndDeletedAtIsNull(String name, Organisation organisation);

        // Organisation-scoped queries — EntityGraph prevents N+1 on common relationships
        @EntityGraph(attributePaths = {"organisation", "department", "category", "location", "assignedUser", "supplier"})
        List<Asset> findAllByOrganisationAndDeletedAtIsNull(Organisation organisation);

        List<Asset> findAllByOrganisationAndCreatedByAndDeletedAtIsNull(Organisation organisation, String createdBy);

        List<Asset> findAllByOrganisationAndCreatedByInOrCreatedByIsNullAndDeletedAtIsNull(Organisation organisation,
                        java.util.Collection<String> createdByList);

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

        Set<Asset> findByOrganisationAndCategoryIdAndDeletedAtIsNull(Organisation organisation, UUID categoryId);

        long countByOrganisationAndDeletedAtIsNull(Organisation organisation);

        /** Assets whose warranty expires on or before {@code cutoff} and are not yet disposed. */
        @Query("SELECT a FROM Asset a WHERE a.deletedAt IS NULL AND a.warrantyExpiryDate IS NOT NULL AND a.warrantyExpiryDate <= :cutoff AND a.status <> 'DISPOSED'")
        List<Asset> findWarrantyExpiringSoon(@Param("cutoff") LocalDate cutoff);

        /** Active assets with purchaseDate + usefulLifeMonths set — caller filters for EOL in Java. */
        @Query("SELECT a FROM Asset a WHERE a.deletedAt IS NULL AND a.purchaseDate IS NOT NULL AND a.usefulLifeMonths IS NOT NULL AND a.status <> 'DISPOSED'")
        List<Asset> findActiveAssetsWithUsefulLife();
}
