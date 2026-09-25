package com.assetiq.repositories;

import com.assetiq.enums.AssetStatus;
import com.assetiq.models.Asset;
import com.assetiq.models.Department;
import com.assetiq.models.Organisation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID>, JpaSpecificationExecutor<Asset> {
        List<Asset> findAllByDeletedAtIsNull();

        List<Asset> findAllByCreatedByAndDeletedAtIsNull(String createdBy);

        Optional<Asset> findByIdAndDeletedAtIsNull(UUID id);

        Optional<Asset> findByNameAndOrganisationAndDeletedAtIsNull(String name, Organisation organisation);

        Optional<Asset> findByNameIgnoreCaseAndDeletedAtIsNull(String name);

        boolean existsByNameIgnoreCaseAndDeletedAtIsNull(String name);

        boolean existsByNameIgnoreCaseAndOrganisationAndDepartmentAndDeletedAtIsNull(
                        String name, Organisation organisation, Department department);

        boolean existsByNameIgnoreCaseAndOrganisationAndDeletedAtIsNull(String name, Organisation organisation);

        /** Rename checks: the same scopes as create, excluding the asset being renamed. */
        boolean existsByNameIgnoreCaseAndOrganisationAndDepartmentAndDeletedAtIsNullAndIdNot(
                        String name, Organisation organisation, Department department, java.util.UUID id);

        boolean existsByNameIgnoreCaseAndOrganisationAndDeletedAtIsNullAndIdNot(
                        String name, Organisation organisation, java.util.UUID id);

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

        /** Tenant-scoped tag lookup: an asset tag is only unique within an organisation (V46). */
        Optional<Asset> findByAssetTagIgnoreCaseAndOrganisationAndDeletedAtIsNull(String assetTag,
                        Organisation organisation);

        /**
         * Asset tags in the organisation that start with {@code prefix}, including
         * deleted assets so a generated tag never reuses a retired number.
         */
        @Query("select a.assetTag from Asset a where a.organisation = :org and a.assetTag like concat(:prefix, '%')")
        List<String> findAssetTagsStartingWith(@Param("org") Organisation org, @Param("prefix") String prefix);

        Optional<Asset> findBySerialNumberAndDeletedAtIsNull(String serialNumber);

        Set<Asset> findByDepartmentIdAndDeletedAtIsNull(UUID departmentId);

        Set<Asset> findByLocationIdAndDeletedAtIsNull(UUID locationId);

        /** How many live assets sit at this location — the delete guard's count. */
        long countByLocationIdAndDeletedAtIsNull(UUID locationId);

        Set<Asset> findByAssignedUserIdAndDeletedAtIsNull(UUID userId);

        Set<Asset> findByStatusAndDeletedAtIsNull(AssetStatus status);

        Set<Asset> findByOrganisationIdAndStatusAndDeletedAtIsNull(UUID organisationId, AssetStatus status);

        Set<Asset> findByCategoryIdAndDeletedAtIsNull(UUID categoryId);

        Set<Asset> findByOrganisationAndCategoryIdAndDeletedAtIsNull(Organisation organisation, UUID categoryId);

        long countByOrganisationAndDeletedAtIsNull(Organisation organisation);

        /** Per-status counts for the org — returns [status (String), count (Long)] rows. */
        @Query("SELECT a.status, COUNT(a) FROM Asset a WHERE a.organisation = :org AND a.deletedAt IS NULL GROUP BY a.status")
        List<Object[]> countGroupedByStatus(@Param("org") Organisation org);

        /**
         * The tenant's most recently updated live assets, newest first, as slim
         * rows for the mobile Home screen. Page with {@code PageRequest.of(0, n)};
         * rows never written since {@code updated_at} existed sort last, and id
         * breaks ties so the order is stable between refreshes.
         */
        @Query("SELECT new com.assetiq.dto.mobile.RecentAsset(a.id, a.name, a.assetTag, a.status, a.updatedAt) "
                        + "FROM Asset a WHERE a.organisation = :org AND a.deletedAt IS NULL "
                        + "ORDER BY a.updatedAt DESC NULLS LAST, a.id")
        List<com.assetiq.dto.mobile.RecentAsset> findRecentlyUpdated(@Param("org") Organisation org, Pageable page);

        /** Count assets with an assigned user. */
        @Query("SELECT COUNT(a) FROM Asset a WHERE a.organisation = :org AND a.deletedAt IS NULL AND a.assignedUser IS NOT NULL")
        long countAssigned(@Param("org") Organisation org);

        /** Assets whose warranty expires on or before {@code cutoff} and are not yet disposed. */
        @Query("SELECT a FROM Asset a WHERE a.deletedAt IS NULL AND a.warrantyExpiryDate IS NOT NULL AND a.warrantyExpiryDate <= :cutoff AND a.status <> 'DISPOSED'")
        List<Asset> findWarrantyExpiringSoon(@Param("cutoff") LocalDate cutoff);

        @Query("SELECT a FROM Asset a WHERE a.deletedAt IS NULL " +
                        "AND a.warrantyExpiryDate = :expiryDate " +
                        "AND a.status NOT IN ('DISPOSED','RETIRED')")
        Page<Asset> findWarrantyExpiringOn(
                        @Param("expiryDate") LocalDate expiryDate, Pageable pageable);

        /** Active assets with purchaseDate + usefulLifeMonths set — caller filters for EOL in Java. */
        @Query("SELECT a FROM Asset a WHERE a.deletedAt IS NULL AND a.purchaseDate IS NOT NULL AND a.usefulLifeMonths IS NOT NULL AND a.status <> 'DISPOSED'")
        List<Asset> findActiveAssetsWithUsefulLife();

        @Query("SELECT a FROM Asset a WHERE a.deletedAt IS NULL " +
                        "AND a.purchaseDate IS NOT NULL AND a.usefulLifeMonths IS NOT NULL " +
                        "AND a.status NOT IN ('DISPOSED','RETIRED')")
        Page<Asset> findActiveAssetsWithUsefulLife(Pageable pageable);

        /** Purchase cost of the organisation's on-book (non-disposed) assets, summed per currency. */
        @Query("SELECT a.currency, SUM(a.purchaseCost) FROM Asset a WHERE a.organisation = :org " +
                        "AND a.deletedAt IS NULL AND (a.status IS NULL OR a.status <> 'DISPOSED') " +
                        "AND a.purchaseCost IS NOT NULL GROUP BY a.currency")
        List<Object[]> sumOnBookPurchaseCostByCurrency(@Param("org") Organisation org);

        /** Every live, non-disposed asset across all tenants, for the monthly book-value refresh. */
        @Query("SELECT a FROM Asset a WHERE a.deletedAt IS NULL " +
                        "AND (a.status IS NULL OR a.status <> 'DISPOSED')")
        Page<Asset> findUndisposedForDepreciation(Pageable pageable);

        @Query("SELECT a FROM Asset a WHERE a.deletedAt IS NULL " +
                        "AND a.insurancePolicyExpiry = :expiryDate " +
                        "AND a.status NOT IN ('DISPOSED','RETIRED')")
        Page<Asset> findInsuranceExpiringOn(
                        @Param("expiryDate") LocalDate expiryDate, Pageable pageable);

        @Query("SELECT a FROM Asset a WHERE a.deletedAt IS NULL " +
                        "AND a.status = 'IN_STOCK' " +
                        "AND (a.lastScannedAt IS NULL OR a.lastScannedAt <= :cutoff)")
        Page<Asset> findInactiveInStock(
                        @Param("cutoff") Instant cutoff, Pageable pageable);

        /**
         * Every live asset of one tenant as a slim valuation row: the columns the
         * depreciation engine needs plus the identifiers the UI links on.
         *
         * <p>Cost: one pass over {@code asset} filtered by
         * {@code (organisation_id, deleted_at)} with four left joins on primary
         * keys ({@code department}, {@code location}, {@code category},
         * {@code depreciation_policy}). No entity is hydrated, no lazy proxy is
         * touched, and the category policy — an N+1 when assets are loaded as
         * entities — arrives in the same row.
         */
        @Query("SELECT new com.assetiq.services.insights.AssetValuationRow("
                        + "a.id, a.name, a.assetTag, a.currency, a.purchaseCost, a.residualValue, "
                        + "a.usefulLifeMonths, a.depreciationMethod, a.purchaseDate, a.warrantyExpiryDate, "
                        + "a.insurancePolicyExpiry, a.status, a.condition, u.id, "
                        + "d.id, d.name, l.id, l.name, c.id, c.name, "
                        + "p.usefulLifeMonths, p.method, p.salvageValuePercent, a.updatedAt, a.lastScannedAt) "
                        + "FROM Asset a "
                        + "LEFT JOIN a.assignedUser u "
                        + "LEFT JOIN a.department d "
                        + "LEFT JOIN a.location l "
                        + "LEFT JOIN a.category c "
                        + "LEFT JOIN c.depreciationPolicy p "
                        + "WHERE a.organisation = :org AND a.deletedAt IS NULL")
        List<com.assetiq.services.insights.AssetValuationRow> findValuationRows(@Param("org") Organisation org);

        /**
         * Assets whose warranty lapses on or before {@code cutoff} (already lapsed
         * included), scoped to one tenant, as slim due-rows. The amount is the
         * original purchase cost — AssetIQ cannot price the cover itself, and the
         * caller says so rather than implying the figure is a book value.
         *
         * <p>Cost: an index range scan on {@code warranty_expiry_date} within the
         * tenant plus a primary-key join to {@code department}.
         */
        @Query("SELECT new com.assetiq.services.insights.DueRow("
                        + "a.id, a.name, a.assetTag, a.warrantyExpiryDate, a.purchaseCost, null, a.currency, "
                        + "d.id, d.name) "
                        + "FROM Asset a LEFT JOIN a.department d "
                        + "WHERE a.organisation = :org AND a.deletedAt IS NULL "
                        + "AND a.warrantyExpiryDate IS NOT NULL AND a.warrantyExpiryDate <= :cutoff "
                        + "AND a.status NOT IN ('DISPOSED','RETIRED') "
                        + "ORDER BY a.warrantyExpiryDate ASC")
        List<com.assetiq.services.insights.DueRow> findWarrantyDueBy(
                        @Param("org") Organisation org, @Param("cutoff") LocalDate cutoff);

        /** The same, for insurance policy expiry; the amount is the annual premium. */
        @Query("SELECT new com.assetiq.services.insights.DueRow("
                        + "a.id, a.name, a.assetTag, a.insurancePolicyExpiry, a.insurancePremiumPerYear, "
                        + "a.purchaseCost, a.currency, d.id, d.name) "
                        + "FROM Asset a LEFT JOIN a.department d "
                        + "WHERE a.organisation = :org AND a.deletedAt IS NULL "
                        + "AND a.insurancePolicyExpiry IS NOT NULL AND a.insurancePolicyExpiry <= :cutoff "
                        + "AND a.status NOT IN ('DISPOSED','RETIRED') "
                        + "ORDER BY a.insurancePolicyExpiry ASC")
        List<com.assetiq.services.insights.DueRow> findInsuranceDueBy(
                        @Param("org") Organisation org, @Param("cutoff") LocalDate cutoff);

        /** Live assets by status for one tenant, for the nightly snapshot. */
        @Query("SELECT COUNT(a) FROM Asset a WHERE a.organisation = :org AND a.deletedAt IS NULL "
                        + "AND a.status IN ('IN_USE','IN_STOCK','RESERVED')")
        long countActive(@Param("org") Organisation org);
}
