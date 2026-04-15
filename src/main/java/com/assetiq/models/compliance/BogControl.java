package com.assetiq.models.compliance;

import com.assetiq.models.BaseEntity;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Bank of Ghana (BOG) ICT Security Directive control tracking.
 * Maps to the BOG ICT Directive requirements for licensed financial institutions.
 */
@Entity
@Table(name = "bog_control",
        uniqueConstraints = @UniqueConstraint(name = "uq_bog_ctrl_org_ref",
                columnNames = {"organisation_id", "directive_ref"}),
        indexes = @Index(name = "idx_bog_ctrl_org", columnList = "organisation_id"))
@Getter
@Setter
public class BogControl extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    /** BOG directive reference, e.g. "ICT.4.1", "ORM.2.3" */
    @Column(name = "directive_ref", nullable = false, length = 32)
    private String directiveRef;

    @Column(name = "requirement", columnDefinition = "TEXT", nullable = false)
    private String requirement;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ControlStatus status = ControlStatus.NOT_IMPLEMENTED;

    @Column(name = "evidence_url")
    private String evidenceUrl;

    @Column(name = "gap_description", columnDefinition = "TEXT")
    private String gapDescription;

    @Column(name = "remediation_plan", columnDefinition = "TEXT")
    private String remediationPlan;

    @Column(name = "target_date")
    private Instant targetDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;
}
