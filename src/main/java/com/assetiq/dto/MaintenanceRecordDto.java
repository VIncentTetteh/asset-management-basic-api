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

    private MaintenanceStatus status;

    private LocalDate nextDueDate;
}

