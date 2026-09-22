package com.assetiq.dto.compliance;

import com.assetiq.models.compliance.SecurityIncident;
import com.assetiq.validation.NullOrNotBlank;
import com.assetiq.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class SecurityIncidentDto {

    private UUID id;
    private UUID organisationId;

    @NotBlank(groups = OnCreate.class, message = "Title is required")
    @NullOrNotBlank
    @Size(max = 255)
    private String title;

    private String description;

    @NotNull(groups = OnCreate.class, message = "Severity is required")
    private SecurityIncident.Severity severity;

    @Size(max = 64)
    private String category;
    private UUID reportedById;
    private String reportedByEmail;
    private UUID assignedToId;
    private String assignedToEmail;
    private Instant detectedAt;
    private Instant resolvedAt;
    private String rootCause;
    private String lessonsLearned;
    private SecurityIncident.IncidentStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
