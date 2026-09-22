package com.assetiq.dto.compliance;

import com.assetiq.models.compliance.RegulatoryFiling;
import com.assetiq.validation.NullOrNotBlank;
import com.assetiq.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class RegulatoryFilingDto {

    private UUID id;
    private UUID organisationId;

    @NotBlank(groups = OnCreate.class, message = "Filing type is required")
    @NullOrNotBlank
    @Size(max = 255)
    private String filingType;

    @NotBlank(groups = OnCreate.class, message = "Regulator is required")
    @NullOrNotBlank
    @Size(max = 32)
    private String regulator;

    @NotNull(groups = OnCreate.class, message = "Due date is required")
    private Instant dueDate;

    private Instant submittedAt;
    @Size(max = 128)
    private String reference;
    private RegulatoryFiling.FilingStatus status;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
