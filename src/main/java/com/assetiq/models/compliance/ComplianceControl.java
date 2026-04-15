package com.assetiq.models.compliance;

import com.assetiq.models.BaseEntity;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Tracks implementation status of a specific control within any compliance framework.
 * Used for ISO 27001 (e.g. A.9.1.1), SOC 2 (CC6.1), PCI-DSS (Req 6.3), ICS, BOG.
 */
@Entity
@Table(name = "compliance_control",
        indexes = {
                @Index(name = "idx_comp_ctrl_org_framework", columnList = "organisation_id,framework"),
                @Index(name = "idx_comp_ctrl_org_status", columnList = "organisation_id,status")
        })
@Getter
@Setter
public class ComplianceControl extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @Enumerated(EnumType.STRING)
    @Column(name = "framework", nullable = false, length = 32)
    private ComplianceFramework framework;

    @Column(name = "control_ref", nullable = false, length = 64)
    private String controlRef;

    @Column(name = "control_name", nullable = false)
    private String controlName;

    @Column(name = "control_description", columnDefinition = "TEXT")
    private String controlDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ControlStatus status = ControlStatus.NOT_IMPLEMENTED;

    @Column(name = "justification", columnDefinition = "TEXT")
    private String justification;

    @Column(name = "evidence_url")
    private String evidenceUrl;

    @Column(name = "gap_description", columnDefinition = "TEXT")
    private String gapDescription;

    @Column(name = "remediation_plan", columnDefinition = "TEXT")
    private String remediationPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(name = "review_due_date")
    private Instant reviewDueDate;

    @Column(name = "last_reviewed_at")
    private Instant lastReviewedAt;

    @Column(name = "last_reviewed_by")
    private String lastReviewedByEmail;
}
