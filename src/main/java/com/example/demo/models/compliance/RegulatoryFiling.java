package com.example.demo.models.compliance;

import com.example.demo.models.BaseEntity;
import com.example.demo.models.Organisation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Tracks regulatory submissions to BOG and other regulators (SEC, NCA, etc.).
 */
@Entity
@Table(name = "regulatory_filing",
        indexes = @Index(name = "idx_reg_filing_org", columnList = "organisation_id,due_date"))
@Getter
@Setter
public class RegulatoryFiling extends BaseEntity {

    public enum FilingStatus { PENDING, SUBMITTED, OVERDUE, ACKNOWLEDGED, REJECTED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @Column(name = "filing_type", nullable = false)
    private String filingType;

    @Column(name = "regulator", nullable = false, length = 32)
    private String regulator;

    @Column(name = "due_date", nullable = false)
    private Instant dueDate;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reference", length = 128)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private FilingStatus status = FilingStatus.PENDING;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
