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
    private int skipped = 0;
    @Column(columnDefinition = "text")
    private String errorsJson;
    @Column(length = 2000)
    private String errorSummary;
}
