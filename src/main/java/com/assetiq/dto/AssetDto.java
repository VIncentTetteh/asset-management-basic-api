package com.assetiq.dto;

import com.assetiq.enums.*;
import com.assetiq.validation.NullOrNotBlank;
import com.assetiq.validation.OnCreate;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;


import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class AssetDto {
    private UUID id;

    @NotBlank(groups = OnCreate.class, message = "Asset name is required")
    @NullOrNotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String assetTag;

    @Size(max = 255)
    private String serialNumber;

    @Size(max = 255)
    private String barcodeQrCode;

    private String description;

    private UUID categoryId;

    private AssetType assetType;

    @Size(max = 255)
    private String manufacturer;

    @Size(max = 255)
    private String model;

    private LocalDate purchaseDate;

    @PositiveOrZero
    @Digits(integer = 13, fraction = 2)
    private BigDecimal purchaseCost;

    @Size(max = 3)
    private String currency;

    private DepreciationMethod depreciationMethod;

    @Min(1)
    private Integer usefulLifeMonths;

    @PositiveOrZero
    @Digits(integer = 13, fraction = 2)
    private BigDecimal residualValue;

    @Digits(integer = 13, fraction = 2)
    private BigDecimal currentBookValue;

    private LocalDate warrantyExpiryDate;

    private AssetStatus status;

    private AssetCondition condition;

    private UUID locationId;

    private UUID assignedUserId;

    private UUID supplierId;

    @Size(max = 255)
    private String invoiceId;

    @Size(max = 255)
    private String insurancePolicyId;

    private UUID departmentId;

    private UUID organisationId;

    private UUID purchaseOrderId;

    private com.assetiq.enums.ProcurementType procurementType;

    @Size(max = 100)
    private String costCenter;

    // ── TCO inputs (read by the total-cost-of-ownership report) ──

    /** Annual insurance premium, in the asset's currency. */
    @PositiveOrZero
    @Digits(integer = 13, fraction = 2)
    private BigDecimal insurancePremiumPerYear;

    /** Cost of one day of downtime, in the asset's currency. */
    @PositiveOrZero
    @Digits(integer = 13, fraction = 2)
    private BigDecimal downtimeCostPerDay;

    private LocalDate insurancePolicyExpiry;

    /** The asset this one is a component of (e.g. a server's disk). */
    private UUID parentAssetId;

    private Instant createdAt;

    private Instant updatedAt;

    // ── Depreciation (read-only, computed by DepreciationCalculator as of today) ──
    // currentBookValue above is also computed live on read.

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private BigDecimal accumulatedDepreciation;

    /** Charge for the month of service in progress; zero when fully depreciated or disposed. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private BigDecimal monthlyDepreciation;

    /** False when no useful life (asset or category policy) or purchase date is available. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Boolean depreciationConfigured;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Boolean fullyDepreciated;

    /** Method actually applied (asset field, else category policy, else straight-line). */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private DepreciationMethod effectiveDepreciationMethod;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer effectiveUsefulLifeMonths;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private BigDecimal effectiveResidualValue;

    /**
     * Update only: optional relations to clear, by DTO field name. A null field in
     * an update means "leave unchanged", so clearing needs to be explicit. Allowed:
     * {@code departmentId}, {@code locationId}, {@code supplierId},
     * {@code purchaseOrderId}, {@code assignedUserId}, {@code parentAssetId},
     * {@code insurancePremiumPerYear}, {@code downtimeCostPerDay}, {@code insurancePolicyExpiry}.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private List<String> clearFields;
}
