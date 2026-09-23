package com.assetiq.repositories;

import com.assetiq.enums.LicenseStatus;
import com.assetiq.models.Organisation;
import com.assetiq.models.SoftwareLicense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SoftwareLicenseRepository extends JpaRepository<SoftwareLicense, UUID> {

    List<SoftwareLicense> findByOrganisationAndDeletedAtIsNull(Organisation organisation);

    Optional<SoftwareLicense> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    List<SoftwareLicense> findByOrganisationAndStatusAndDeletedAtIsNull(
            Organisation organisation, LicenseStatus status);

    /** Licenses expiring on or before a given date. */
    @Query("SELECT l FROM SoftwareLicense l WHERE l.organisation = :org " +
           "AND l.deletedAt IS NULL AND l.expiryDate IS NOT NULL AND l.expiryDate <= :cutoff " +
           "ORDER BY l.expiryDate ASC")
    List<SoftwareLicense> findExpiringSoon(
            @Param("org") Organisation org, @Param("cutoff") LocalDate cutoff);

    /** Licenses where used seats >= total seats (over-used). */
    @Query("SELECT l FROM SoftwareLicense l WHERE l.organisation = :org " +
           "AND l.deletedAt IS NULL AND l.totalSeats IS NOT NULL AND l.usedSeats >= l.totalSeats")
    List<SoftwareLicense> findOverAllocated(@Param("org") Organisation org);

    boolean existsByLicenseKeyAndOrganisationIdAndDeletedAtIsNull(String licenseKey, UUID organisationId);

    @Query("SELECT l FROM SoftwareLicense l WHERE l.deletedAt IS NULL " +
            "AND l.expiryDate = :expiryDate")
    Page<SoftwareLicense> findExpiringOn(
            @Param("expiryDate") LocalDate expiryDate, Pageable pageable);

    /**
     * Expiry job: ACTIVE or EXPIRING_SOON licences whose expiry date has passed become
     * EXPIRED, across all organisations; auto-renewing licences are left alone.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE SoftwareLicense l SET l.status = com.assetiq.enums.LicenseStatus.EXPIRED, l.updatedAt = :now "
            + "WHERE l.deletedAt IS NULL AND l.expiryDate < :today AND (l.autoRenew IS NULL OR l.autoRenew = false) "
            + "AND l.status IN (com.assetiq.enums.LicenseStatus.ACTIVE, com.assetiq.enums.LicenseStatus.EXPIRING_SOON)")
    int expirePastExpiryDate(@Param("today") LocalDate today, @Param("now") java.time.Instant now);

    /**
     * Live licences expiring on or before {@code cutoff}, including ones already
     * past expiry, as slim due-rows. The renewal cost is the primary figure and
     * the purchase cost the fallback.
     *
     * <p>Cost: an index range scan on {@code expiry_date} within the tenant.
     */
    @Query("SELECT new com.assetiq.services.insights.DueRow("
            + "l.id, l.name, l.vendor, l.expiryDate, l.annualRenewalCost, l.purchaseCost, l.currency, "
            + "a.id, a.name) "
            + "FROM SoftwareLicense l LEFT JOIN l.asset a "
            + "WHERE l.organisation = :org AND l.deletedAt IS NULL "
            + "AND l.expiryDate IS NOT NULL AND l.expiryDate <= :cutoff "
            + "AND l.status <> com.assetiq.enums.LicenseStatus.CANCELLED "
            + "ORDER BY l.expiryDate ASC")
    List<com.assetiq.services.insights.DueRow> findDueBy(@Param("org") Organisation org,
                                                         @Param("cutoff") LocalDate cutoff);

    /** Seats purchased and seats in use across the tenant's live, uncancelled licences. */
    @Query("SELECT COALESCE(SUM(l.totalSeats), 0), COALESCE(SUM(l.usedSeats), 0) "
            + "FROM SoftwareLicense l WHERE l.organisation = :org AND l.deletedAt IS NULL "
            + "AND l.status NOT IN (com.assetiq.enums.LicenseStatus.CANCELLED, "
            + "com.assetiq.enums.LicenseStatus.EXPIRED)")
    List<Object[]> sumSeats(@Param("org") Organisation org);

}
