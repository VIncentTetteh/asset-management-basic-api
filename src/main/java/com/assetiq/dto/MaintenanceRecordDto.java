package com.assetiq.dto;

import com.assetiq.enums.MaintenanceStatus;
import com.assetiq.enums.MaintenanceType;
import com.assetiq.validation.OnCreate;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class MaintenanceRecordDto {
    private UUID id;

    @NotNull(groups = OnCreate.class, message = "Asset ID is required")
    private UUID assetId;

    @NotNull(groups = OnCreate.class, message = "Maintenance type is required")
    private MaintenanceType maintenanceType;

    private String description;

    private LocalDate scheduledDate;

    private LocalDate performedDate;

    private UUID vendorId;

    @PositiveOrZero
    @Digits(integer = 13, fraction = 2)
    private BigDecimal cost;

    /** ISO-4217 code of {@code cost}; defaults to the asset's currency when omitted on create. */
    @Size(max = 3)
    private String currency;

    private MaintenanceStatus status;

    private LocalDate nextDueDate;
}

