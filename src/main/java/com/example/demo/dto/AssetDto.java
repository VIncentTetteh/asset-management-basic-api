package com.example.demo.dto;

import com.example.demo.enums.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class AssetDto {
    private UUID id;

    @NotBlank(message = "Asset name is required")
    private String name;

    @NotBlank(message = "Asset tag is required")
    private String assetTag;

    private String serialNumber;

    private String barcodeQrCode;

    private String description;

    private UUID categoryId;

    @NotNull(message = "Asset type is required")
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

    @NotNull(message = "Department ID is required")
    private UUID departmentId;

    @NotNull(message = "Organisation ID is required")
    private UUID organisationId;
}
