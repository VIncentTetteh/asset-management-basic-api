package com.example.demo.dto.compliance;

import com.example.demo.models.compliance.SecurityPolicy;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class SecurityPolicyDto {

    private UUID id;
    private UUID organisationId;

    @NotBlank(message = "Title is required")
    private String title;

    private String version;
    private String documentUrl;
    private UUID ownerId;
    private String ownerEmail;
    private String approvedByEmail;
    private Instant effectiveDate;
    private Instant reviewDueDate;
    private SecurityPolicy.PolicyStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
