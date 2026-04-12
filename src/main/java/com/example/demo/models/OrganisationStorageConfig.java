package com.example.demo.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Per-organisation S3 storage configuration.
 *
 * <p>Uses Option C (prefix-based isolation): all organisations share the
 * application's IAM role — no per-org credentials are stored anywhere.
 * Isolation is enforced by scoping every object key under the org's own
 * prefix: {@code {reportPrefix}/{orgId}/{objectId}/{filename}}.
 *
 * <p>Admins can enable/disable S3 per organisation, customise the key
 * prefixes, choose an alternative bucket, and control presigned URL TTL.
 */
@Entity
@Table(
    name = "organisation_storage_config",
    indexes = {
        @Index(name = "idx_org_storage_org_id", columnList = "organisation_id")
    }
)
@Data
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class OrganisationStorageConfig extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false, unique = true)
    private Organisation organisation;

    /**
     * Master switch.  When {@code false} the org falls back to in-memory
     * storage (no files are persisted beyond the JVM lifecycle).
     */
    @Column(name = "s3_enabled", nullable = false)
    private boolean s3Enabled = false;

    /**
     * Optional bucket override.  When blank the application-level
     * {@code app.storage.s3.bucket} is used.
     */
    @Column(name = "bucket_name", length = 255)
    private String bucketName;

    /** Object-key prefix for generated reports.  Defaults to {@code "reports"}. */
    @Column(name = "report_prefix", length = 200, nullable = false)
    private String reportPrefix = "reports";

    /** Object-key prefix for import/upload files.  Defaults to {@code "imports"}. */
    @Column(name = "import_prefix", length = 200, nullable = false)
    private String importPrefix = "imports";

    /** Minutes before a presigned download URL expires.  Defaults to {@code 15}. */
    @Column(name = "presign_minutes", nullable = false)
    private int presignMinutes = 15;
}
