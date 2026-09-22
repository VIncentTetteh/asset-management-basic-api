package com.assetiq.dto.compliance;

import com.assetiq.validation.HttpUrl;
import com.assetiq.models.compliance.SecurityPolicy;
import com.assetiq.validation.NullOrNotBlank;
import com.assetiq.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class SecurityPolicyDto {

    private UUID id;
    private UUID organisationId;

    @NotBlank(groups = OnCreate.class, message = "Title is required")
    @NullOrNotBlank
    @Size(max = 255)
    private String title;

    @Size(max = 16)
    private String version;
    @Size(max = 255)
    @HttpUrl
    private String documentUrl;
    private UUID ownerId;
    private String ownerEmail;
    @Size(max = 255)
    private String approvedByEmail;
    private Instant effectiveDate;
    private Instant reviewDueDate;
    private SecurityPolicy.PolicyStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
