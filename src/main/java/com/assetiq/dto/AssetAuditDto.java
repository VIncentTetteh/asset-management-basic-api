package com.assetiq.dto;

import com.assetiq.enums.AuditStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


import java.time.LocalDate;
import java.util.UUID;

@Data
public class AssetAuditDto {
    private UUID id;

    @NotNull(message = "Organisation ID is required")
    private UUID organisationId;

    @NotNull(message = "Department ID is required")
    private UUID departmentId;

    @NotNull(message = "Audit date is required")
    private LocalDate auditDate;

    @NotNull(message = "Conducted by user ID is required")
    private UUID conductedById;

    private AuditStatus status;

    private String remarks;
}

