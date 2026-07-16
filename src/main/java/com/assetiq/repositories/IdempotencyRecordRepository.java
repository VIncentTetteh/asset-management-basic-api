package com.assetiq.repositories;

import com.assetiq.models.IdempotencyRecord;
import com.assetiq.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByOrganisationAndOperationAndIdempotencyKeyAndDeletedAtIsNull(
            Organisation organisation,
            String operation,
            String idempotencyKey
    );

    /**
     * Purge idempotency records older than the given cutoff instant.
     * Used by {@link com.assetiq.jobs.IdempotencyCleanupJob} for TTL-based cleanup.
     */
    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
