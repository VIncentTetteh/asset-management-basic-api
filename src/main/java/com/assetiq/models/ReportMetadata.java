package com.assetiq.models;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Entity
@Table(name = "report_metadata", indexes = {
        @Index(name = "idx_report_metadata_org_created", columnList = "organisation_id,created_at")
})
@Data
public class ReportMetadata extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @Column(nullable = false, length = 80)
    private String reportType;

    @Column(nullable = false, length = 20)
    private String format;

    @Column(nullable = false, length = 120)
    private String filename;

    @Column(nullable = false, length = 200)
    private String contentType;

    @Column(nullable = false, length = 500)
    private String storageKey;
}

