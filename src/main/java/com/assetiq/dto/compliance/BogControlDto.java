package com.assetiq.dto.compliance;

import com.assetiq.models.compliance.ControlStatus;
import com.assetiq.validation.NullOrNotBlank;
import com.assetiq.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class BogControlDto {

    private UUID id;
    private UUID organisationId;

    @NotBlank(groups = OnCreate.class, message = "Directive reference is required")
    @NullOrNotBlank
    @Size(max = 32)
    private String directiveRef;

    @NotBlank(groups = OnCreate.class, message = "Requirement text is required")
    @NullOrNotBlank
    private String requirement;

    private ControlStatus status;
    @Size(max = 255)
    private String evidenceUrl;
    private String gapDescription;
    private String remediationPlan;
    private Instant targetDate;
    private UUID ownerId;
    private String ownerEmail;
    private Instant createdAt;
    private Instant updatedAt;
}
