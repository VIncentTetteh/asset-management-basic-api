package com.assetiq.dto;

import com.assetiq.enums.TransferStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID requestedById;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID approvedById;

    private LocalDate transferDate;

    private TransferStatus status;

    private String reason;
}
