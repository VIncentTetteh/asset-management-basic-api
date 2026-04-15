package com.assetiq.dto.compliance;

import com.assetiq.models.compliance.ComplianceFramework;
import com.assetiq.models.compliance.ControlStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class ComplianceControlDto {

    private UUID id;
    private UUID organisationId;

    @NotNull(message = "Framework is required")
    private ComplianceFramework framework;

    @NotBlank(message = "Control reference is required")
    private String controlRef;

    @NotBlank(message = "Control name is required")
    private String controlName;

    private String controlDescription;
    private ControlStatus status;
    private String justification;
    private String evidenceUrl;
    private String gapDescription;
    private String remediationPlan;
    private UUID ownerId;
    private String ownerEmail;
    private Instant reviewDueDate;
    private Instant lastReviewedAt;
    private String lastReviewedByEmail;
    private Instant createdAt;
    private Instant updatedAt;
}
