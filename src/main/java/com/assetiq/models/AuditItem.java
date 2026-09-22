package com.assetiq.models;

import com.assetiq.enums.AuditDiscrepancyType;
import com.assetiq.enums.AuditItemStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One line of an audit count sheet: an asset that is in scope for an
 * {@link AssetAudit}, and what the auditor found when they went looking for it.
 *
 * <p>Items are generated when the audit starts (or on demand) — one per in-scope
 * asset — and then verified one at a time, usually by scanning the asset's QR
 * label. An audit's progress, and whether it can show as verified at all, is
 * counted from these rows.
 *
 * <p>One live item per asset per audit: partial unique index
 * uq_audit_item_audit_asset_live (V57, WHERE deleted_at IS NULL). JPA cannot
 * declare a partial index, so there is no {@code @UniqueConstraint} here.
 */
@Entity
@Getter
@Setter
@Table(name = "audit_item")
public class AuditItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audit_id", nullable = false)
    private AssetAudit audit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AuditItemStatus status = AuditItemStatus.PENDING;

    /** Where the register said the asset was when the sheet was generated. */
    @Column(name = "expected_location")
    private String expectedLocation;

    /** Where the auditor actually found it. */
    @Column(name = "actual_location")
    private String actualLocation;

    @Column(name = "condition")
    private String condition;

    /**
     * Kept in step with {@link #status} so the pre-V57 reads of this column (and
     * the repository finders built on it) keep telling the truth.
     */
    @Column(name = "discrepancy_flag")
    private Boolean discrepancyFlag = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "discrepancy_type", length = 30)
    private AuditDiscrepancyType discrepancyType;

    @Column(name = "discrepancy_reason", columnDefinition = "TEXT")
    private String discrepancyReason;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_id")
    private User verifiedBy;

    @Column(columnDefinition = "TEXT")
    private String remarks;
}
