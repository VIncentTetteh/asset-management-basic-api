package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * API response for an organisation's S3 storage configuration.
 */
@Data
@Builder
public class OrganisationStorageConfigResponse {

    private UUID id;
    private UUID organisationId;
    private String organisationName;

    private boolean s3Enabled;

    /** Effective bucket in use (org-specific override, or global if blank). */
    private String bucketName;

    private String reportPrefix;
    private String importPrefix;
    private int presignMinutes;

    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String modifiedBy;
}
