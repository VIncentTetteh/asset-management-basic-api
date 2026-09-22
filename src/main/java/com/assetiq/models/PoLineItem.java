package com.assetiq.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One itemised line of a {@link PurchaseOrder} (V56).
 *
 * <p>Line numbers are unique among live rows only: partial unique index
 * uq_po_line_item_order_line_live (WHERE deleted_at IS NULL). JPA cannot declare a
 * partial index, so no {@code @UniqueConstraint} here — declaring the full one
 * would claim soft-deleted rows count, and replacing an order's lines soft-deletes
 * the old ones and reuses their numbers in the same transaction.
 */
@Entity
@Getter
@Setter
@Table(name = "po_line_item")
public class PoLineItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    /** 1-based position within the order; renumbered whenever the set is replaced. */
    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "supplier_part_number", length = 100)
    private String supplierPartNumber;

    /** Optional asset category this line will be booked against. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(precision = 15, scale = 4, nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_price", precision = 19, scale = 4, nullable = false)
    private BigDecimal unitPrice;

    /** Percentage (e.g. 12.5000 for 12.5%). Null means the line carries no tax. */
    @Column(name = "tax_rate", precision = 9, scale = 4)
    private BigDecimal taxRate;

    /** Money. Derived from taxRate when one is given, otherwise taken as supplied. */
    @Column(name = "tax_amount", precision = 19, scale = 4)
    private BigDecimal taxAmount;

    /** quantity x unitPrice + taxAmount, stored so a later tax rule cannot restate history. */
    @Column(name = "line_total", precision = 19, scale = 4, nullable = false)
    private BigDecimal lineTotal;
}
