package com.assetiq.models.compliance;

import com.assetiq.models.Asset;
import com.assetiq.models.BaseEntity;
import com.assetiq.models.Organisation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Tracks firmware/software patch history for ICS assets (IEC 62443 / NERC CIP).
 */
@Entity
@Table(name = "patch_record",
        indexes = @Index(name = "idx_patch_record_org", columnList = "organisation_id,applied_at"))
@Getter
@Setter
public class PatchRecord extends BaseEntity {

    public enum PatchStatus { PLANNED, APPLIED, FAILED, ROLLED_BACK }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "patch_name", nullable = false)
    private String patchName;

    @Column(name = "version", length = 64)
    private String version;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "applied_by_email")
    private String appliedByEmail;

    @Column(name = "test_env_validated")
    private Boolean testEnvironmentValidated = false;

    @Column(name = "rollback_plan", columnDefinition = "TEXT")
    private String rollbackPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PatchStatus status = PatchStatus.PLANNED;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
