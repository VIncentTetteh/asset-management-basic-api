package com.assetiq.repositories;

import com.assetiq.enums.AuditDiscrepancyType;
import com.assetiq.enums.AuditItemStatus;
import com.assetiq.models.AuditItem;
import com.assetiq.models.Organisation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every finder here is scoped to an organisation. Rows left organisation-less by
 * V57 (items that predate the audit_id column and carry only the legacy
 * asset_audit_id) are therefore invisible to every tenant, which is the safe
 * outcome for data nobody can prove the owner of.
 */
@Repository
public interface AuditItemRepository extends JpaRepository<AuditItem, UUID> {

    List<AuditItem> findByAuditIdAndOrganisationAndDeletedAtIsNull(UUID auditId, Organisation organisation);

    Optional<AuditItem> findByIdAndAuditIdAndOrganisationAndDeletedAtIsNull(
            UUID id, UUID auditId, Organisation organisation);

    Optional<AuditItem> findByAuditIdAndAssetIdAndOrganisationAndDeletedAtIsNull(
            UUID auditId, UUID assetId, Organisation organisation);

    long countByAuditIdAndOrganisationAndDeletedAtIsNull(UUID auditId, Organisation organisation);

    long countByAuditIdAndOrganisationAndStatusAndDeletedAtIsNull(
            UUID auditId, Organisation organisation, AuditItemStatus status);

    /** [auditId, status, count] for a batch of audits — one query instead of three per audit. */
    @Query("SELECT i.audit.id, i.status, COUNT(i) FROM AuditItem i"
            + " WHERE i.organisation = :organisation"
            + "   AND i.audit.id IN :auditIds"
            + "   AND i.deletedAt IS NULL"
            + " GROUP BY i.audit.id, i.status")
    List<Object[]> countByAuditAndStatus(@Param("organisation") Organisation organisation,
                                         @Param("auditIds") Collection<UUID> auditIds);

    /**
     * {@code searchPattern} is always a real string — {@code %} for "no filter" —
     * rather than null. A null String parameter leaves Postgres inferring
     * {@code bytea} for it, and {@code lower(bytea)} does not exist.
     */
    @Query("SELECT i FROM AuditItem i"
            + " WHERE i.audit.id = :auditId"
            + "   AND i.organisation = :organisation"
            + "   AND i.deletedAt IS NULL"
            + "   AND (:status IS NULL OR i.status = :status)"
            + "   AND (:discrepancyType IS NULL OR i.discrepancyType = :discrepancyType)"
            + "   AND (LOWER(COALESCE(i.asset.assetTag, '')) LIKE :searchPattern"
            + "        OR LOWER(COALESCE(i.asset.name, '')) LIKE :searchPattern)")
    Page<AuditItem> search(@Param("auditId") UUID auditId,
                           @Param("organisation") Organisation organisation,
                           @Param("status") AuditItemStatus status,
                           @Param("discrepancyType") AuditDiscrepancyType discrepancyType,
                           @Param("searchPattern") String searchPattern,
                           Pageable pageable);

    /**
     * Per asset, the latest moment a physical audit confirmed it was there.
     *
     * <p>The strongest sighting AssetIQ holds: somebody stood in front of the
     * asset and said so. Cost: one grouped scan of the tenant's audit items,
     * restricted to verified rows. Rows are {@code [assetId, verifiedAt]}.
     */
    @Query("SELECT i.asset.id, MAX(i.verifiedAt) FROM AuditItem i "
            + "WHERE i.organisation = :organisation AND i.deletedAt IS NULL "
            + "AND i.asset IS NOT NULL AND i.verifiedAt IS NOT NULL GROUP BY i.asset.id")
    List<Object[]> findLatestVerificationPerAsset(@Param("organisation") Organisation organisation);

}
