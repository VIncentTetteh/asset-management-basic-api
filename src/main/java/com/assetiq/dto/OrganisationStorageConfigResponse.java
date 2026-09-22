package com.assetiq.dto;

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

    /** The organisation's own bucket, or null when it uses the default. Edit this, not bucketName. */
    private String bucketOverride;

    /** The server-wide default bucket ({@code app.storage.s3.bucket}), or null if none is set. */
    private String defaultBucket;

    private String reportPrefix;
    private String importPrefix;
    private int presignMinutes;

    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String modifiedBy;
}
