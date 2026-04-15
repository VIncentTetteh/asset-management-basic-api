package com.assetiq.models.compliance;

import com.assetiq.models.BaseEntity;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "risk_register",
        indexes = {
                @Index(name = "idx_risk_org_status", columnList = "organisation_id,status"),
                @Index(name = "idx_risk_org_score", columnList = "organisation_id,risk_score")
        })
@Getter
@Setter
public class RiskRegister extends BaseEntity {

    public enum RiskTreatment { ACCEPT, MITIGATE, TRANSFER, AVOID }
    public enum RiskStatus { OPEN, IN_TREATMENT, CLOSED, ACCEPTED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @Enumerated(EnumType.STRING)
    @Column(name = "framework", length = 32)
    private ComplianceFramework framework;

    @Column(name = "risk_id", length = 32)
    private String riskId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** 1 (rare) to 5 (almost certain) */
    @Column(name = "likelihood", nullable = false)
    private Integer likelihood;

    /** 1 (negligible) to 5 (catastrophic) */
    @Column(name = "impact", nullable = false)
    private Integer impact;

    /** Computed: likelihood × impact */
    @Column(name = "risk_score", nullable = false)
    private Integer riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "treatment", length = 16)
    private RiskTreatment treatment;

    @Column(name = "mitigation_plan", columnDefinition = "TEXT")
    private String mitigationPlan;

    @Column(name = "residual_risk")
    private Integer residualRisk;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RiskStatus status = RiskStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(name = "review_date")
    private Instant reviewDate;

    @PrePersist
    @PreUpdate
    public void computeScore() {
        if (likelihood != null && impact != null) {
            this.riskScore = likelihood * impact;
        }
    }
}
