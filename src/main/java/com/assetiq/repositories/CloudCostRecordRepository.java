package com.assetiq.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.assetiq.models.CloudAsset;
import com.assetiq.models.CloudCostRecord;
import com.assetiq.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CloudCostRecordRepository extends JpaRepository<CloudCostRecord, UUID> {

    List<CloudCostRecord> findByCloudAssetAndDeletedAtIsNull(CloudAsset asset);

    /** Cost history of one asset; order comes from the pageable. */
    Page<CloudCostRecord> findByCloudAssetAndDeletedAtIsNull(CloudAsset asset, Pageable pageable);

    Optional<CloudCostRecord> findFirstByCloudAssetAndBillingMonthAndServiceNameAndDeletedAtIsNull(
            CloudAsset asset, LocalDate billingMonth, String serviceName);

    /** The whole-asset record for a month (no sub-service); NULLs never match with "=". */
    Optional<CloudCostRecord> findFirstByCloudAssetAndBillingMonthAndServiceNameIsNullAndDeletedAtIsNull(
            CloudAsset asset, LocalDate billingMonth);

    List<CloudCostRecord> findByOrganisationAndBillingMonthAndDeletedAtIsNull(
            Organisation organisation, LocalDate billingMonth);

    @Query("SELECT SUM(r.amount) FROM CloudCostRecord r WHERE r.organisation = :org " +
           "AND r.billingMonth = :month AND r.deletedAt IS NULL")
    BigDecimal sumByOrganisationAndMonth(@Param("org") Organisation org, @Param("month") LocalDate month);

    @Query("SELECT r FROM CloudCostRecord r WHERE r.cloudAsset = :asset AND r.deletedAt IS NULL " +
           "ORDER BY r.billingMonth DESC")
    List<CloudCostRecord> findRecentByAsset(@Param("asset") CloudAsset asset);
}
