package com.assetiq.repositories;

import com.assetiq.enums.TransferStatus;
import com.assetiq.models.AssetTransfer;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface AssetTransferRepository extends JpaRepository<AssetTransfer, UUID> {
    Set<AssetTransfer> findByAssetId(UUID assetId);

    Set<AssetTransfer> findByFromDepartmentId(UUID departmentId);

    Set<AssetTransfer> findByToDepartmentId(UUID departmentId);

    Set<AssetTransfer> findByRequestedById(UUID userId);

    // Soft-delete scoped
    Optional<AssetTransfer> findByIdAndDeletedAtIsNull(UUID id);

    Set<AssetTransfer> findByAssetIdAndDeletedAtIsNull(UUID assetId);

    Set<AssetTransfer> findByFromDepartmentIdAndDeletedAtIsNull(UUID departmentId);

    Set<AssetTransfer> findByToDepartmentIdAndDeletedAtIsNull(UUID departmentId);

    Set<AssetTransfer> findByRequestedByIdAndDeletedAtIsNull(UUID userId);

    // Organisation-scoped
    Set<AssetTransfer> findByOrganisationAndDeletedAtIsNull(Organisation organisation);

    /**
     * Transfers in {@code status} that {@code approver} did not request. With
     * {@code REQUESTED} this is the approval queue: approveTransfer refuses a
     * requester approving their own transfer, so those are not waiting on them.
     */
    @Query("SELECT COUNT(t) FROM AssetTransfer t WHERE t.organisation = :org AND t.deletedAt IS NULL "
            + "AND t.status = :status AND t.requestedBy <> :approver")
    long countByStatusRequestedByOther(@Param("org") Organisation org,
                                       @Param("status") TransferStatus status,
                                       @Param("approver") User approver);

    /**
     * The transfers {@link #countByStatusRequestedByOther} counts, oldest request
     * first, as slim rows for the mobile Home queue. Page with
     * {@code PageRequest.of(0, n)}.
     */
    @Query("SELECT new com.assetiq.services.mobile.TransferQueueRow("
            + "t.id, a.id, a.name, u.firstName, u.lastName, u.email, t.status, t.createdAt) "
            + "FROM AssetTransfer t LEFT JOIN t.asset a LEFT JOIN t.requestedBy u "
            + "WHERE t.organisation = :org AND t.deletedAt IS NULL "
            + "AND t.status = :status AND t.requestedBy <> :approver "
            + "ORDER BY t.createdAt ASC NULLS LAST, t.id")
    java.util.List<com.assetiq.services.mobile.TransferQueueRow> findQueueByStatusRequestedByOther(
            @Param("org") Organisation org,
            @Param("status") TransferStatus status,
            @Param("approver") User approver,
            org.springframework.data.domain.Pageable page);
}
