package com.example.demo.models.compliance;

import com.example.demo.models.BaseEntity;
import com.example.demo.models.Organisation;
import com.example.demo.models.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "security_incident",
        indexes = {
                @Index(name = "idx_incident_org_status", columnList = "organisation_id,status"),
                @Index(name = "idx_incident_org_severity", columnList = "organisation_id,severity")
        })
@Getter
@Setter
public class SecurityIncident extends BaseEntity {

    public enum Severity { P1_CRITICAL, P2_HIGH, P3_MEDIUM, P4_LOW }
    public enum IncidentStatus { OPEN, IN_PROGRESS, RESOLVED, CLOSED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private Severity severity;

    @Column(name = "category", length = 64)
    private String category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_by_id")
    private User reportedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private User assignedTo;

    @Column(name = "detected_at")
    private Instant detectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;

    @Column(name = "lessons_learned", columnDefinition = "TEXT")
    private String lessonsLearned;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private IncidentStatus status = IncidentStatus.OPEN;
}
