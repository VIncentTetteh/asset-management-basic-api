package com.assetiq.repositories;

import com.assetiq.enums.AuditEventType;
import com.assetiq.models.AuditEvent;
import com.assetiq.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    Optional<AuditEvent> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    /** @deprecated Use {@link #findFiltered} instead — avoids full table scan. */
    @Deprecated(since = "phase-4", forRemoval = true)
    List<AuditEvent> findByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(Organisation organisation);

    /**
     * P4-C: Server-side filtered query — all parameters are optional (pass null to skip).
     * Filtering is pushed to the database so no rows are loaded unnecessarily.
     */
    @Query("""
            SELECT e FROM AuditEvent e
            WHERE e.organisation = :org
              AND e.deletedAt IS NULL
              AND (:actorId    IS NULL OR e.actor.id    = :actorId)
              AND (:start      IS NULL OR e.createdAt  >= :start)
              AND (:end        IS NULL OR e.createdAt  <= :end)
              AND (:success    IS NULL OR e.success     = :success)
              AND (:method     IS NULL OR UPPER(e.method) = UPPER(:method))
              AND (:path       IS NULL OR e.path LIKE CONCAT('%', :path, '%'))
              AND (:eventType  IS NULL OR e.eventType   = :eventType)
            ORDER BY e.createdAt DESC
            """)
    List<AuditEvent> findFiltered(
            @Param("org")       Organisation   org,
            @Param("actorId")   UUID           actorId,
            @Param("start")     Instant        start,
            @Param("end")       Instant        end,
            @Param("success")   Boolean        success,
            @Param("method")    String         method,
            @Param("path")      String         path,
            @Param("eventType") AuditEventType eventType
    );

    @Modifying
    @Transactional
    @Query("update AuditEvent e set e.deletedAt = :deletedAt " +
            "where e.organisation = :org and e.deletedAt is null and e.createdAt < :cutoff")
    int softDeleteByOrganisationCreatedAtBeforeAndDeletedAtIsNull(
            @Param("org") Organisation org,
            @Param("cutoff") Instant cutoff,
            @Param("deletedAt") Instant deletedAt
    );

    @Query("SELECT e FROM AuditEvent e WHERE e.organisation = :org " +
           "AND LOWER(e.path) LIKE LOWER(CONCAT('%', :assetId, '%')) " +
           "AND e.deletedAt IS NULL ORDER BY e.createdAt DESC")
    List<AuditEvent> findByOrganisationAndAssetIdInPath(@Param("org") Organisation org, @Param("assetId") String assetId);
}

