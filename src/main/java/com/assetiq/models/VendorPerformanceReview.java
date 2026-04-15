package com.assetiq.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Records a periodic performance evaluation of a supplier/vendor.
 * Enables trend analysis and procurement decision support.
 */
@Entity
@Getter
@Setter
@Table(name = "vendor_performance_review")
public class VendorPerformanceReview extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    /** Overall rating out of 5.00 */
    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal rating;

    /** Delivery punctuality score (1-5) */
    private Integer deliveryScore;

    /** Quality score (1-5) */
    private Integer qualityScore;

    /** Support/communication score (1-5) */
    private Integer supportScore;

    /** Free-text feedback */
    @Column(columnDefinition = "TEXT")
    private String feedback;

    /** Review period start */
    private LocalDate periodStart;

    /** Review period end */
    private LocalDate periodEnd;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id", nullable = false)
    private User reviewedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;
}
