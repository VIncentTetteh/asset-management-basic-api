package com.assetiq.models;

import com.assetiq.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "purchase_order", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "po_number", "organisation_id" }, name = "uk_po_number_per_org")
})
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
