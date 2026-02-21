package com.example.demo.models;

import com.example.demo.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Setter
@Getter
@Entity
@Table(name = "asset", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"asset_tag", "organisation_id"},
                      name = "uk_asset_tag_per_organisation"),
    @UniqueConstraint(columnNames = {"serial_number", "organisation_id"},
                      name = "uk_serial_number_per_organisation")
})
public class Asset extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String assetTag;

    @Column(unique = true)
    private String serialNumber;

    @Column(unique = true)
    private String barcodeQrCode;

    @Column(nullable = false)
    private String description;

    @ManyToOne
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
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

    @ManyToOne
    private Location location;

    @ManyToOne
    private User assignedUser;

    @ManyToOne
    private Supplier supplier;

    private String invoiceId;

    private String insurancePolicyId;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Department department;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Organisation organisation;

}
