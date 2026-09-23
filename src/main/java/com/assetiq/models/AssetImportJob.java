package com.assetiq.models;

import com.assetiq.enums.ImportJobStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;
@Entity
@Table(name = "asset_import_job", indexes = {
        @Index(name = "idx_asset_import_job_status", columnList = "status"),
        @Index(name = "idx_asset_import_job_org_status", columnList = "organisation_id,status")
})
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
public class AssetImportJob extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ImportJobStatus status = ImportJobStatus.QUEUED;
    @Column(nullable = false)
    private boolean dryRun = false;

    /**
     * Which record type this job imports, as an
     * {@link com.assetiq.imports.ImportEntityType} name.
     *
     * <p>The job machinery — queue, async worker, status polling, stuck-job recovery —
     * is shared by every entity type rather than duplicated, so the type lives on the
     * row. Legacy rows and legacy callers mean ASSETS, hence the default.</p>
     */
    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType = "ASSETS";

    /** The user's column mapping as JSON (field name to column index), or null for the
     *  historical positional asset layout. */
    @Column(name = "mapping_json", columnDefinition = "text")
    private String mappingJson;

    /** Duplicate handling, reference creation and dry run, as JSON. Null means defaults. */
    @Column(name = "options_json", columnDefinition = "text")
    private String optionsJson;
    // Stored location of the uploaded Excel file.
    @Column(nullable = false, length = 500)
    private String storageKey;
    @Column(nullable = false, length = 300)
    private String filename;
    @Column(length = 200)
    private String contentType;
    // Result snapshot (written at job completion).
    private int totalRows = 0;
    private int imported = 0;
    @Column(name = "updated_rows", nullable = false)
    private int updatedRows = 0;
    private int skipped = 0;

    /**
     * Rows that failed, as distinct from rows merely not written. Kept separately so the
     * stored result can honour the invariant that the error list is exactly as long as
     * the failure count — the counts on the old row could not, which is how a result
     * screen showed one row and two failures at the same time.
     */
    @Column(name = "failed_rows", nullable = false)
    private int failedRows = 0;

    /** SUCCESS, PARTIAL, FAILED or NOTHING_TO_IMPORT. Null on rows written before V65. */
    @Column(name = "outcome", length = 30)
    private String outcome;

    /** Per-row leniencies as JSON: values left blank, columns not imported. */
    @Column(name = "notes_json", columnDefinition = "text")
    private String notesJson;

    /** Records and custom fields the run created on the caller's behalf, as JSON. */
    @Column(name = "created_json", columnDefinition = "text")
    private String createdJson;

    /** Why the run stopped before the end of the file, if it did. */
    @Column(name = "stopped_reason", length = 2000)
    private String stoppedReason;

    @Column(columnDefinition = "text")
    private String errorsJson;
    @Column(length = 2000)
    private String errorSummary;
}
