package com.assetiq.dto;

import com.assetiq.enums.MaintenanceStatus;
import com.assetiq.enums.MaintenanceType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class MaintenanceRecordDto {
    private UUID id;

    @NotNull(message = "Asset ID is required")
    private UUID assetId;

    @NotNull(message = "Maintenance type is required")
    private MaintenanceType maintenanceType;

    private String description;

    private LocalDate scheduledDate;

    private LocalDate performedDate;

    private UUID vendorId;

    private BigDecimal cost;

    /** ISO-4217 code of {@code cost}; defaults to the asset's currency when omitted on create. */
    private String currency;

    private MaintenanceStatus status;

    private LocalDate nextDueDate;
}

