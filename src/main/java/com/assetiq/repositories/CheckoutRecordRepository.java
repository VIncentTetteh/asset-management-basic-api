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

    /**
     * Checkouts in {@code status} whose expected return date is before
     * {@code today} — the count behind CheckoutService#listOverdue, which filters
     * ACTIVE records the same way. A record with no expected return date is
     * never overdue.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(c) FROM CheckoutRecord c WHERE c.organisation = :org AND c.deletedAt IS NULL "
            + "AND c.status = :status AND c.expectedReturnDate IS NOT NULL AND c.expectedReturnDate < :today")
    long countPastExpectedReturn(
            @org.springframework.data.repository.query.Param("org") Organisation org,
            @org.springframework.data.repository.query.Param("status") CheckoutStatus status,
            @org.springframework.data.repository.query.Param("today") java.time.LocalDate today);

    /**
     * The checkouts {@link #countPastExpectedReturn} counts, oldest expected
     * return first, as slim rows for the mobile Home queue. Page with
     * {@code PageRequest.of(0, n)}.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT new com.assetiq.services.mobile.CheckoutQueueRow("
            + "c.id, a.id, a.name, u.firstName, u.lastName, e.firstName, e.lastName, c.status, "
            + "c.expectedReturnDate) "
            + "FROM CheckoutRecord c LEFT JOIN c.asset a LEFT JOIN c.checkedOutBy u LEFT JOIN c.employee e "
            + "WHERE c.organisation = :org AND c.deletedAt IS NULL "
            + "AND c.status = :status AND c.expectedReturnDate IS NOT NULL AND c.expectedReturnDate < :today "
            + "ORDER BY c.expectedReturnDate ASC, c.id")
    List<com.assetiq.services.mobile.CheckoutQueueRow> findPastExpectedReturnQueue(
            @org.springframework.data.repository.query.Param("org") Organisation org,
            @org.springframework.data.repository.query.Param("status") CheckoutStatus status,
            @org.springframework.data.repository.query.Param("today") java.time.LocalDate today,
            org.springframework.data.domain.Pageable page);
}
