package com.assetiq.repositories;

import com.assetiq.models.Organisation;
import com.assetiq.models.VendorPerformanceReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorPerformanceReviewRepository extends JpaRepository<VendorPerformanceReview, UUID> {

    List<VendorPerformanceReview> findBySupplierIdAndDeletedAtIsNull(UUID supplierId);

    List<VendorPerformanceReview> findByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(Organisation organisation);

    Optional<VendorPerformanceReview> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    @Query("SELECT AVG(v.rating) FROM VendorPerformanceReview v WHERE v.supplier.id = :supplierId AND v.deletedAt IS NULL")
    Double averageRatingBySupplierId(@Param("supplierId") UUID supplierId);
}
