package com.assetiq.repositories;

import com.assetiq.enums.LeaseStatus;
import com.assetiq.models.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.util.*;

public interface LeaseRecordRepository extends JpaRepository<LeaseRecord, UUID> {
    List<LeaseRecord> findByOrganisationAndDeletedAtIsNull(Organisation org);
    List<LeaseRecord> findByAssetAndDeletedAtIsNull(Asset asset);
    Optional<LeaseRecord> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation org);

    @Query("SELECT l FROM LeaseRecord l WHERE l.organisation = :org AND l.status = 'ACTIVE' AND l.endDate <= :cutoff AND l.deletedAt IS NULL")
    List<LeaseRecord> findExpiringSoon(@Param("org") Organisation org, @Param("cutoff") LocalDate cutoff);

    @Query("SELECT l FROM LeaseRecord l WHERE l.status = 'ACTIVE' " +
            "AND l.endDate = :expiryDate AND l.deletedAt IS NULL")
    Page<LeaseRecord> findActiveExpiringOn(
            @Param("expiryDate") LocalDate expiryDate, Pageable pageable);

    /**
     * Expiry job: ACTIVE leases whose end date has passed become EXPIRED, across all
     * organisations; auto-renewing leases are left alone.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE LeaseRecord r SET r.status = com.assetiq.enums.LeaseStatus.EXPIRED, r.updatedAt = :now "
            + "WHERE r.deletedAt IS NULL AND r.endDate < :today AND (r.autoRenew IS NULL OR r.autoRenew = false) "
            + "AND r.status = com.assetiq.enums.LeaseStatus.ACTIVE")
    int expirePastEndDate(@Param("today") LocalDate today, @Param("now") java.time.Instant now);

    /**
     * Active leases ending on or before {@code cutoff}, as slim due-rows. The
     * amount is the monthly payment — what continues to be paid if the notice
     * period is missed.
     *
     * <p>Cost: an index range scan on {@code end_date} within the tenant plus
     * primary-key joins to {@code asset} and {@code supplier}.
     */
    @Query("SELECT new com.assetiq.services.insights.DueRow("
            + "r.id, s.name, a.assetTag, r.endDate, r.monthlyPayment, null, r.currency, a.id, a.name) "
            + "FROM LeaseRecord r LEFT JOIN r.asset a LEFT JOIN r.lessor s "
            + "WHERE r.organisation = :org AND r.deletedAt IS NULL AND r.endDate <= :cutoff "
            + "AND r.status = com.assetiq.enums.LeaseStatus.ACTIVE "
            + "ORDER BY r.endDate ASC")
    List<com.assetiq.services.insights.DueRow> findDueBy(@Param("org") Organisation org,
                                                         @Param("cutoff") LocalDate cutoff);

}
