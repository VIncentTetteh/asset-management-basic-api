package com.assetiq.models.compliance;

import com.assetiq.models.BaseEntity;
import com.assetiq.models.Organisation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * PCI-DSS Self-Assessment Questionnaire (SAQ) answers per requirement.
 */
@Entity
@Table(name = "pci_saq_record",
        uniqueConstraints = @UniqueConstraint(name = "uq_pci_saq_org_req",
                columnNames = {"organisation_id", "requirement_number"}),
        indexes = @Index(name = "idx_pci_saq_org", columnList = "organisation_id"))
@Getter
@Setter
public class PciSaqRecord extends BaseEntity {

    public enum ComplianceAnswer { YES, NO, NOT_APPLICABLE, COMPENSATING_CONTROL }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @Column(name = "requirement_number", nullable = false, length = 16)
    private String requirementNumber;

    @Column(name = "requirement_text", columnDefinition = "TEXT")
    private String requirementText;

    @Enumerated(EnumType.STRING)
    @Column(name = "compliance_status", nullable = false, length = 24)
    private ComplianceAnswer complianceStatus = ComplianceAnswer.NO;

    @Column(name = "compensating_control", columnDefinition = "TEXT")
    private String compensatingControl;

    @Column(name = "evidence_url")
    private String evidenceUrl;

    @Column(name = "target_date")
    private Instant targetDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
