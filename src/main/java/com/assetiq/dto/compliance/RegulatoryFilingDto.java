package com.assetiq.dto.compliance;

import com.assetiq.models.compliance.RegulatoryFiling;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class RegulatoryFilingDto {

    private UUID id;
    private UUID organisationId;

    @NotBlank(message = "Filing type is required")
    private String filingType;

    @NotBlank(message = "Regulator is required")
    private String regulator;

    @NotNull(message = "Due date is required")
    private Instant dueDate;

    private Instant submittedAt;
    private String reference;
    private RegulatoryFiling.FilingStatus status;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
