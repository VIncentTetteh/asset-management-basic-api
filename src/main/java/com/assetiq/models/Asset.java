package com.assetiq.models;

import com.assetiq.enums.*;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;


import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "asset", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "asset_tag", "organisation_id" }, name = "uk_asset_tag_per_organisation"),
        @UniqueConstraint(columnNames = { "serial_number",
                "organisation_id" }, name = "uk_serial_number_per_organisation")
})
@Data
@Setter
@Getter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Asset extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column
    private String assetTag;

    @Column
    private String serialNumber;

    @Column
    private String barcodeQrCode;

    // Stored as TEXT: the column is free text with no meaningful upper bound, and
    // the migrated schema declares it TEXT. Pinning a varchar length here would
    // both fail ddl-auto=validate and invite a truncating migration.
    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    private Category category;

    @Enumerated(EnumType.STRING)
    private AssetType assetType;

    private String manufacturer;

    private String model;

    private LocalDate purchaseDate;

    @Column(precision = 15, scale = 2)
    private BigDecimal purchaseCost;

    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    private DepreciationMethod depreciationMethod = DepreciationMethod.STRAIGHT_LINE;

    private Integer usefulLifeMonths;

    @Column(precision = 15, scale = 2)
    private BigDecimal residualValue;

    @Column(precision = 15, scale = 2)
    private BigDecimal currentBookValue;

    private LocalDate warrantyExpiryDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AssetStatus status = AssetStatus.IN_USE;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AssetCondition condition = AssetCondition.GOOD;

    @ManyToOne(fetch = FetchType.LAZY)
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    private User assignedUser;

    @ManyToOne(fetch = FetchType.LAZY)
    private Supplier supplier;

    private String invoiceId;

    private String insurancePolicyId;

    @ManyToOne(fetch = FetchType.LAZY)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Organisation organisation;

    /**
     * The purchase order that sourced this asset — enables PO→Asset lifecycle
     * traceability.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    private PurchaseOrder purchaseOrder;

    /**
     * Whether this asset was acquired as CAPEX (owned/depreciated) or OPEX
     * (leased/subscribed). Required for financial reporting in regulated institutions.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ProcurementType procurementType;

    /**
     * Cost centre / GL code for financial allocation (e.g. "IT-OPS-001").
     */
    @Column(length = 100)
    private String costCenter;

    // ── Asset Bundling ────────────────────────────────────────────────────────

    /** Parent asset for bundle hierarchies (null = top-level asset). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_asset_id")
    private Asset parentAsset;

    // ── Insurance Tracking ────────────────────────────────────────────────────

    /** Insurance policy expiry date — drives insurance renewal alerts. */
    private LocalDate insurancePolicyExpiry;

    /** Annual insurance premium for TCO calculation. */
    @Column(precision = 15, scale = 2)
    private BigDecimal insurancePremiumPerYear;

    // ── TCO (Total Cost of Ownership) ─────────────────────────────────────────

    /** Configurable daily downtime cost for TCO calculation. */
    @Column(precision = 15, scale = 2)
    private BigDecimal downtimeCostPerDay;

    // ── QR Scan Audit ─────────────────────────────────────────────────────────

    /** Timestamp of the most recent QR scan. */
    private Instant lastScannedAt;

    /** User who performed the most recent QR scan. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_scanned_by_id")
    private User lastScannedBy;
}
