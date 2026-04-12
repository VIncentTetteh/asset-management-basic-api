package com.example.demo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    private String bucketName;

    /** Key prefix for generated reports.  Defaults to {@code "reports"}. */
    @Size(max = 200, message = "Report prefix must be at most 200 characters")
    private String reportPrefix;

    /** Key prefix for import/upload files.  Defaults to {@code "imports"}. */
    @Size(max = 200, message = "Import prefix must be at most 200 characters")
    private String importPrefix;

    /** Presigned URL TTL in minutes (1–10080). */
    @Min(value = 1,     message = "Presign minutes must be at least 1")
    @Max(value = 10080, message = "Presign minutes cannot exceed 10080 (7 days)")
    private Integer presignMinutes;
}
