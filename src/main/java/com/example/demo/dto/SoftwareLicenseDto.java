package com.example.demo.dto;

import com.example.demo.enums.LicenseStatus;
import com.example.demo.enums.LicenseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class SoftwareLicenseDto {

    private UUID id;

    @NotBlank(message = "License name is required")
    private String name;

    @NotBlank(message = "Vendor is required")
    private String vendor;

    private String licenseKey;
    private String productName;
    private String version;

    @NotNull(message = "License type is required")
    private LicenseType licenseType;

    private LicenseStatus status;

    private Integer totalSeats;
    private Integer usedSeats;

    private BigDecimal purchaseCost;
    private BigDecimal annualRenewalCost;
    private String currency;

    private LocalDate purchaseDate;
    private LocalDate expiryDate;
    private LocalDate renewalDate;

    private Boolean autoRenew;
    private String licenseDocumentUrl;
    private String notes;

    private UUID assetId;
    private UUID organisationId;

    // Computed / enrichment fields
    private Integer availableSeats;
    private Long daysUntilExpiry;
}
