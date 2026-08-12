package com.assetiq.models;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "qr_revisions")
@Getter
@Setter
public class QrRevision extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    // Stored as TEXT: the column is free text with no meaningful upper bound, and
    // the migrated schema declares it TEXT. Pinning a varchar length here would
    // both fail ddl-auto=validate and invite a truncating migration.
    @Column(columnDefinition = "TEXT")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_by_id")
    private User generatedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;
}
