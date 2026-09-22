package com.assetiq.dto.compliance;

import com.assetiq.models.compliance.ComplianceFramework;
import com.assetiq.models.compliance.ControlStatus;
import com.assetiq.validation.NullOrNotBlank;
import com.assetiq.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class ComplianceControlDto {

    private UUID id;
    private UUID organisationId;

    @NotNull(groups = OnCreate.class, message = "Framework is required")
    private ComplianceFramework framework;

    @NotBlank(groups = OnCreate.class, message = "Control reference is required")
    @NullOrNotBlank
    @Size(max = 64)
    private String controlRef;

    @NotBlank(groups = OnCreate.class, message = "Control name is required")
    @NullOrNotBlank
    @Size(max = 255)
    private String controlName;

    private String controlDescription;
    private ControlStatus status;
    private String justification;
    @Size(max = 255)
    private String evidenceUrl;
    private String gapDescription;
    private String remediationPlan;
    private UUID ownerId;
    private String ownerEmail;
    private Instant reviewDueDate;
    private Instant lastReviewedAt;
    @Size(max = 255)
    private String lastReviewedByEmail;
    private Instant createdAt;
    private Instant updatedAt;
}
