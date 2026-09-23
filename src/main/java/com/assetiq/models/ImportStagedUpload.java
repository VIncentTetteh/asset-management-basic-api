package com.assetiq.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A file uploaded to the import wizard and not yet committed.
 *
 * <p>The bytes live in the storage service, not here and not in memory: analyse,
 * preview and commit are three separate HTTP requests that may land on three different
 * instances, and an in-memory staging area loses the customer's file on every deploy.</p>
 *
 * <p>Every row carries an organisation and every read is scoped by it, so one tenant
 * cannot preview or commit another's upload even holding the id.</p>
 *
 * <p>{@code expiresAt} is not decoration. A staging area that only grows is a
 * slow-motion outage — see {@code StagedUploadCleanupScheduler}, which deletes the
 * stored object and the row together once the window passes.</p>
 */
@Entity
@Table(name = "import_staged_upload", indexes = {
        @Index(name = "idx_import_staged_upload_org", columnList = "organisation_id"),
        @Index(name = "idx_import_staged_upload_expires", columnList = "expires_at")
})
@Getter
@Setter
public class ImportStagedUpload extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    /** {@link com.assetiq.imports.ImportEntityType} name. */
    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(nullable = false, length = 300)
    private String filename;

    @Column(name = "content_type", length = 200)
    private String contentType;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "column_count", nullable = false)
    private int columnCount;

    /** Detected headers and sample values, cached so preview need not re-read the file. */
    @Column(name = "columns_json", columnDefinition = "text")
    private String columnsJson;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
