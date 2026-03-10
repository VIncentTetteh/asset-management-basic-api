package com.example.demo.dto.compliance;

import com.example.demo.models.compliance.SecurityIncident;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class SecurityIncidentDto {

    private UUID id;
    private UUID organisationId;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "Severity is required")
    private SecurityIncident.Severity severity;

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
