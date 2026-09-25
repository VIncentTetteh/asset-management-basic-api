package com.assetiq.models;

import com.assetiq.enums.AuditEventType;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Table(name = "audit_event", indexes = {
        @Index(name = "idx_audit_event_org_created_at",    columnList = "organisation_id, created_at"),
        @Index(name = "idx_audit_event_actor_created_at",  columnList = "actor_id, created_at"),
        @Index(name = "idx_audit_event_event_type",        columnList = "event_type"),
        @Index(name = "idx_audit_event_target_id",         columnList = "target_id")
})
public class AuditEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id")
    private Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(name = "actor_email")
    private String actorEmail;

    @Column(nullable = false, length = 10)
    private String method;

    // Stored as TEXT: the column is free text with no meaningful upper bound, and
    // the migrated schema declares it TEXT. Pinning a varchar length here would
    // both fail ddl-auto=validate and invite a truncating migration.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String path;

    // Stored as TEXT: the column is free text with no meaningful upper bound, and
    // the migrated schema declares it TEXT. Pinning a varchar length here would
    // both fail ddl-auto=validate and invite a truncating migration.
    @Column(columnDefinition = "TEXT")
    private String query;

    @Column(length = 200)
    private String handler;

    @Column(name = "response_status", nullable = false)
    private Integer responseStatus;

    @Column(nullable = false)
    private Boolean success;

    // Stored as TEXT: the column is free text with no meaningful upper bound, and
    // the migrated schema declares it TEXT. Pinning a varchar length here would
    // both fail ddl-auto=validate and invite a truncating migration.
    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "client_ip", length = 100)
    private String clientIp;

    // Stored as TEXT: the column is free text with no meaningful upper bound, and
    // the migrated schema declares it TEXT. Pinning a varchar length here would
    // both fail ddl-auto=validate and invite a truncating migration.
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    // ── Structured RBAC / security classification (Phase 4) ──────────────────

    /**
     * High-level category of this event.  Defaults to {@link AuditEventType#API_REQUEST}
     * for generic HTTP events; set explicitly for RBAC and auth events.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 50, nullable = false)
    private AuditEventType eventType = AuditEventType.API_REQUEST;

    /**
     * The UUID (as string) of the resource that was the target of an RBAC change
     * (e.g. the Role ID when permissions changed, the User ID when role was assigned).
     */
    @Column(name = "target_id", length = 100)
    private String targetId;

    /**
     * Human-readable snapshot of the value before the change (comma-separated
     * permission names, role name, etc.).  Kept short — max 1000 chars.
     */
    @Column(name = "old_value", length = 1000)
    private String oldValue;

    /**
     * Human-readable snapshot of the value after the change.
     */
    @Column(name = "new_value", length = 1000)
    private String newValue;
}

