package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;


import java.util.UUID;

@Data
public class AuditItemDto {
    private UUID id;

    @NotNull(message = "Audit ID is required")
    private UUID auditId;

    @NotNull(message = "Asset ID is required")
    private UUID assetId;

    private String expectedLocation;

    private String actualLocation;

    private String condition;

    private Boolean discrepancyFlag;

    private String remarks;
}

