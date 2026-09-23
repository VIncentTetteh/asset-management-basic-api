package com.assetiq.repositories;

import com.assetiq.models.AnalyticsSnapshot;
import com.assetiq.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnalyticsSnapshotRepository extends JpaRepository<AnalyticsSnapshot, UUID> {

    boolean existsByOrganisationAndSnapshotDate(Organisation organisation, LocalDate snapshotDate);

    /**
     * One tenant's snapshots from {@code from} onwards, oldest first.
     *
     * <p>Cost: an index range scan on {@code (organisation_id, snapshot_date)},
     * bounded by the window the caller asked for — never the whole history.
     */
    @Query("SELECT s FROM AnalyticsSnapshot s WHERE s.organisation = :org AND s.deletedAt IS NULL "
            + "AND s.snapshotDate >= :from ORDER BY s.snapshotDate ASC")
    List<AnalyticsSnapshot> findSince(@Param("org") Organisation org, @Param("from") LocalDate from);

    /** The tenant's earliest snapshot, so a trend can say how little history it has. */
    @Query("SELECT MIN(s.snapshotDate) FROM AnalyticsSnapshot s "
            + "WHERE s.organisation = :org AND s.deletedAt IS NULL")
    Optional<LocalDate> findEarliestSnapshotDate(@Param("org") Organisation org);
}
