package com.assetiq.repositories;

import com.assetiq.models.Contract;
import com.assetiq.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractRepository extends JpaRepository<Contract, UUID> {

    List<Contract> findByOrganisationAndDeletedAtIsNullOrderByEndDateAsc(Organisation organisation);

    Optional<Contract> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    List<Contract> findBySupplierIdAndDeletedAtIsNull(UUID supplierId);

    List<Contract> findByAssetIdAndDeletedAtIsNull(UUID assetId);

    @Query("SELECT c FROM Contract c WHERE c.organisation = :org AND c.deletedAt IS NULL AND c.endDate <= :cutoff AND c.status NOT IN ('EXPIRED','TERMINATED')")
    List<Contract> findExpiringSoon(@Param("org") Organisation org, @Param("cutoff") LocalDate cutoff);

    @Query("SELECT c FROM Contract c WHERE c.deletedAt IS NULL " +
            "AND c.endDate = :expiryDate AND c.status NOT IN ('EXPIRED','TERMINATED')")
    Page<Contract> findExpiringOn(
            @Param("expiryDate") LocalDate expiryDate, Pageable pageable);

    /**
     * Expiry job: ACTIVE or EXPIRING_SOON contracts whose end date has passed become
     * EXPIRED, across all organisations. Auto-renewing contracts are left alone (they
     * renew rather than lapse). Returns the number of contracts expired.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Contract c SET c.status = com.assetiq.enums.ContractStatus.EXPIRED, c.updatedAt = :now "
            + "WHERE c.deletedAt IS NULL AND c.endDate < :today AND c.autoRenew = false "
            + "AND c.status IN (com.assetiq.enums.ContractStatus.ACTIVE, com.assetiq.enums.ContractStatus.EXPIRING_SOON)")
    int expirePastEndDate(@Param("today") LocalDate today, @Param("now") java.time.Instant now);
}
