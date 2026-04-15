package com.assetiq.dto;

import com.assetiq.enums.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;


import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class AssetDto {
    private UUID id;

    @NotBlank(message = "Asset name is required")
    private String name;

    private String assetTag;

    private String serialNumber;

    private String barcodeQrCode;

    private String description;

    private UUID categoryId;

    private AssetType assetType;

    private String manufacturer;

    private String model;

    private LocalDate purchaseDate;

    private BigDecimal purchaseCost;

    private String currency;

    private DepreciationMethod depreciationMethod;

    private Integer usefulLifeMonths;

    private BigDecimal residualValue;

    private BigDecimal currentBookValue;

    private LocalDate warrantyExpiryDate;

    private AssetStatus status;

    private AssetCondition condition;

    private UUID locationId;

    private UUID assignedUserId;

    private UUID supplierId;

    private String invoiceId;

    private String insurancePolicyId;

    private UUID departmentId;

    private UUID organisationId;

    private UUID purchaseOrderId;

    private com.assetiq.enums.ProcurementType procurementType;

    private String costCenter;

    private Instant createdAt;

    private Instant updatedAt;
}
