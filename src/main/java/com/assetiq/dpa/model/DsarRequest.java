package com.assetiq.dpa.model;

import com.assetiq.models.BaseEntity;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * Data Subject Access Request (DSAR) — Ghana Data Protection Act 2012, s.27.
 *
 * Tracks requests by data subjects to exercise their rights (access, erasure,
 * portability, etc.). Organisations must respond within 30 days.
 */
@Entity
@Table(
    name = "dsar_request",
    indexes = {
        @Index(name = "idx_dsar_request_org",    columnList = "organisation_id"),
        @Index(name = "idx_dsar_request_status", columnList = "status")
    }
)
@Getter @Setter
@ToString(onlyExplicitlyIncluded = true)
public class DsarRequest extends BaseEntity {

    public enum RequestType {
        ACCESS, RECTIFICATION, ERASURE, PORTABILITY, OBJECTION
    }

    public enum Status {
        PENDING, IN_PROGRESS, COMPLETED, REJECTED
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    /** May be null if submitted anonymously (e.g. by a non-registered data subject). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_user_id")
    private User requesterUser;

    @ToString.Include
    @Column(name = "requester_email", nullable = false)
    private String requesterEmail;

    @ToString.Include
    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 30)
    private RequestType requestType;

    @ToString.Include
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.PENDING;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    /** 30-day SLA deadline from submission (Ghana DPA 2012, s.27). */
    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_user_id")
    private User assignedTo;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "response_summary", columnDefinition = "TEXT")
    private String responseSummary;
}
