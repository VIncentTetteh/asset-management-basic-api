package com.assetiq.repositories;

import com.assetiq.enums.LicenseStatus;
import com.assetiq.models.Organisation;
import com.assetiq.models.SoftwareLicense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
