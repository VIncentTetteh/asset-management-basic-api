package com.example.demo.dto;

import com.example.demo.enums.TransferStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class AssetTransferDto {
    private UUID id;

    @NotNull(message = "Asset ID is required")
    private UUID assetId;

    @NotNull(message = "From department ID is required")
    private UUID fromDepartmentId;

    @NotNull(message = "To department ID is required")
    private UUID toDepartmentId;

    private UUID fromLocationId;

    private UUID toLocationId;

    @NotNull(message = "Requested by user ID is required")
    private UUID requestedById;

    private UUID approvedById;

    private LocalDate transferDate;

    private TransferStatus status;

    private String reason;
}

