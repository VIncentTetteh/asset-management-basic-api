package com.assetiq.dto;

import com.assetiq.enums.LicenseStatus;
import com.assetiq.enums.LicenseType;
import com.assetiq.validation.NullOrNotBlank;
import com.assetiq.validation.OnCreate;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class SoftwareLicenseDto {

    private UUID id;

    @NotBlank(groups = OnCreate.class, message = "License name is required")
    @NullOrNotBlank
    @Size(max = 255)
    private String name;

    @NotBlank(groups = OnCreate.class, message = "Vendor is required")
    @NullOrNotBlank
    @Size(max = 255)
    private String vendor;

    @Size(max = 255)
    private String productName;
    @Size(max = 255)
    private String version;

    @NotNull(groups = OnCreate.class, message = "License type is required")
    private LicenseType licenseType;

    private LicenseStatus status;

    @PositiveOrZero
    private Integer totalSeats;
    @PositiveOrZero
    private Integer usedSeats;

    @PositiveOrZero
    @Digits(integer = 13, fraction = 2)
    private BigDecimal purchaseCost;
    @PositiveOrZero
    @Digits(integer = 13, fraction = 2)
    private BigDecimal annualRenewalCost;
    @Size(max = 3)
    private String currency;

    private LocalDate purchaseDate;
    private LocalDate expiryDate;
    private LocalDate renewalDate;

    private Boolean autoRenew;
    @Size(max = 255)
    private String licenseDocumentUrl;
    private String notes;

    private UUID assetId;
    private UUID organisationId;

    // Computed / enrichment fields
    private Integer availableSeats;
    private Long daysUntilExpiry;
}
