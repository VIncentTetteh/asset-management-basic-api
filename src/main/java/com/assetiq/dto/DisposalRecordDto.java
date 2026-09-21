package com.assetiq.dto;

import com.assetiq.enums.DisposalMethod;
import com.assetiq.enums.DisposalStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;


import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class DisposalRecordDto {
    private UUID id;

    @NotNull(message = "Asset ID is required")
    private UUID assetId;

    @NotNull(message = "Disposal method is required")
    private DisposalMethod disposalMethod;

    @NotNull(message = "Disposal date is required")
    private LocalDate disposalDate;

    @DecimalMin(value = "0.00", message = "Sale value cannot be negative")
    private BigDecimal saleValue;

    /** ISO-4217 code of {@code saleValue}; defaults to the asset's currency when omitted on create. */
    private String currency;

    /** Read-only: the request/approve/reject endpoints own it. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private DisposalStatus status;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID requestedById;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID approvedById;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant approvedAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID rejectedById;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant rejectedAt;

    @Size(max = 5000)
    private String reason;

    /** VARCHAR(255) column. */
    @Size(max = 255)
    private String complianceDocumentUrl;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID organisationId;
}
