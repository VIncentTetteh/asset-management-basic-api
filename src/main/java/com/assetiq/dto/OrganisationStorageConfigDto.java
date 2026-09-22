package com.assetiq.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for creating or updating an organisation's S3 storage config.
 *
 * <p>No credentials are required or accepted — the application uses its
 * own IAM role (instance profile / ECS task role).  Per-org isolation is
 * enforced via S3 key prefixes: {@code {reportPrefix}/{orgId}/…}
 */
@Data
public class OrganisationStorageConfigDto {

    /** Enable or disable S3 storage for this organisation. */
    private boolean s3Enabled;

    /**
     * Optional bucket override.  When blank the global
     * {@code app.storage.s3.bucket} is used.
     */
    @Size(max = 255, message = "Bucket name must be at most 255 characters")
    @Pattern(regexp = "^$|^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$",
            message = "Use a valid S3 bucket name: 3 to 63 lower-case letters, digits, dots or hyphens")
    private String bucketName;

    /** Key prefix for generated reports.  Defaults to {@code "reports"}. */
    @Size(max = 200, message = "Report prefix must be at most 200 characters")
    private String reportPrefix;

    /** Key prefix for import/upload files.  Defaults to {@code "imports"}. */
    @Size(max = 200, message = "Import prefix must be at most 200 characters")
    private String importPrefix;

    /**
     * Presigned URL TTL in minutes, 1 to {@link #MAX_PRESIGN_MINUTES} (12 hours).
     * A download link valid for days is a leak waiting to happen; the database
     * CHECK still allows up to 10080 for older rows.
     */
    @Min(value = 1, message = "Presign minutes must be at least 1")
    @Max(value = MAX_PRESIGN_MINUTES, message = "Presign minutes cannot exceed 720 (12 hours)")
    private Integer presignMinutes;

    public static final int MAX_PRESIGN_MINUTES = 720;
}
