package com.example.demo.models;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Table(name = "audit_event", indexes = {
        @Index(name = "idx_audit_event_org_created_at", columnList = "organisation_id, created_at"),
        @Index(name = "idx_audit_event_actor_created_at", columnList = "actor_id, created_at")
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

    @Column(nullable = false, length = 300)
    private String path;

    @Column(length = 500)
    private String query;

    @Column(length = 200)
    private String handler;

    @Column(name = "response_status", nullable = false)
    private Integer responseStatus;

    @Column(nullable = false)
    private Boolean success;

    @Column(length = 500)
    private String message;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "client_ip", length = 100)
    private String clientIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;
}

