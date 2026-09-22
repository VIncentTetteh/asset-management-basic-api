package com.assetiq.models;

import com.assetiq.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


import java.math.BigDecimal;
import java.time.Instant;

// Natural key is unique among live rows only: partial unique index uq_purchase_order_org_number_live
// (V46, WHERE deleted_at IS NULL). JPA cannot declare a partial index, so no
// @UniqueConstraint here; declaring the full one would claim soft-deleted rows count.
@Entity
@Getter
@Setter
@Table(name = "purchase_order")
public class PurchaseOrder extends BaseEntity {

    @Column(nullable = false)
    private String poNumber;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private POStatus status = POStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    private User approvedBy;

    private Instant approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id")
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by_id")
    private User rejectedBy;

    private Instant rejectedAt;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Supplier supplier;

    /** Optional budget this PO should be charged against when approved. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_budget_id")
    private Budget linkedBudget;
}
