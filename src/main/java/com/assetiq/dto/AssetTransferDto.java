package com.assetiq.dto;

import com.assetiq.enums.TransferStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;


import java.time.LocalDate;
import java.util.UUID;

@Data
public class AssetTransferDto {
    private UUID id;

    @NotNull(message = "Asset ID is required")
    private UUID assetId;

    /** Derived from the asset on create; a value sent by the client is ignored. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID fromDepartmentId;

    @NotNull(message = "To department ID is required")
    private UUID toDepartmentId;

    /** Derived from the asset on create; a value sent by the client is ignored. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID fromLocationId;

    private UUID toLocationId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID requestedById;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID approvedById;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID completedById;

    /** When the transfer was requested. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private java.time.Instant createdAt;

    private LocalDate transferDate;

    /** Read-only: the workflow endpoints own the status. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private TransferStatus status;

    @Size(max = 2000)
    private String reason;
}
