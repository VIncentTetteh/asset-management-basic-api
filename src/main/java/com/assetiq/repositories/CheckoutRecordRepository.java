package com.assetiq.repositories;

import com.assetiq.enums.CheckoutStatus;
import com.assetiq.models.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CheckoutRecordRepository extends JpaRepository<CheckoutRecord, UUID> {
    List<CheckoutRecord> findByOrganisationAndDeletedAtIsNull(Organisation org);
    List<CheckoutRecord> findByAssetAndDeletedAtIsNull(Asset asset);
    List<CheckoutRecord> findByCheckedOutByAndDeletedAtIsNull(User user);
    Optional<CheckoutRecord> findByAssetAndStatusAndDeletedAtIsNull(Asset asset, CheckoutStatus status);
    List<CheckoutRecord> findByOrganisationAndStatusAndDeletedAtIsNull(Organisation org, CheckoutStatus status);
    List<CheckoutRecord> findByEmployeeAndDeletedAtIsNullOrderByCheckedOutAtDesc(Employee employee);
    List<CheckoutRecord> findByEmployeeAndStatusAndDeletedAtIsNull(Employee employee, CheckoutStatus status);

    /**
     * Per asset, the latest moment it was handed out and the latest date it was
     * handed back. Both are real handling events — somebody had the thing in
     * their hands — which is what makes them usable as sightings.
     *
     * <p>Cost: one grouped scan of the tenant's checkout records. Rows are
     * {@code [assetId (UUID), lastCheckedOutAt (Instant), lastReturnedOn (LocalDate)]}.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT c.asset.id, MAX(c.checkedOutAt), MAX(c.actualReturnDate) "
            + "FROM CheckoutRecord c WHERE c.organisation = :org AND c.deletedAt IS NULL "
            + "AND c.asset IS NOT NULL GROUP BY c.asset.id")
    List<Object[]> findLatestHandlingPerAsset(
            @org.springframework.data.repository.query.Param("org") Organisation org);

}
