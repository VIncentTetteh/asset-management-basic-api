package com.assetiq.repositories;

import com.assetiq.enums.ImportJobStatus;
import com.assetiq.models.AssetImportJob;
import com.assetiq.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetImportJobRepository extends JpaRepository<AssetImportJob, UUID> {
    Optional<AssetImportJob> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    // Useful for later scheduled retry/cancel implementations.
    Optional<AssetImportJob> findFirstByOrganisationAndStatusAndDeletedAtIsNull(Organisation organisation, ImportJobStatus status);

    /**
     * Returns jobs that are stuck in QUEUED or PROCESSING state beyond the expected window.
     * Used by {@link com.assetiq.scheduling.ImportJobRecoveryScheduler} to detect and
     * re-fire jobs whose async worker was lost (JVM restart, OOM-kill, etc.).
     *
     * @param statuses statuses to check (QUEUED, PROCESSING)
     * @param before   jobs created before this instant are considered stuck
     */
    @Query("SELECT j FROM AssetImportJob j " +
           "WHERE j.status IN :statuses AND j.createdAt < :before AND j.deletedAt IS NULL")
    List<AssetImportJob> findStuckJobs(
            @Param("statuses") List<ImportJobStatus> statuses,
            @Param("before") Instant before);
}

