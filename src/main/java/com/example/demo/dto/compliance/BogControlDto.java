package com.example.demo.dto.compliance;

import com.example.demo.models.compliance.ControlStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class BogControlDto {

    private UUID id;
    private UUID organisationId;

    @NotBlank(message = "Directive reference is required")
    private String directiveRef;

    @NotBlank(message = "Requirement text is required")
    private String requirement;

    private ControlStatus status;
    private String evidenceUrl;
    private String gapDescription;
    private String remediationPlan;
    private Instant targetDate;
    private UUID ownerId;
    private String ownerEmail;
    private Instant createdAt;
    private Instant updatedAt;
}
