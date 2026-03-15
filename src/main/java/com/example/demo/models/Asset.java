package com.example.demo.models;

import com.example.demo.enums.*;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;


import java.math.BigDecimal;
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

    @Column
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

}
