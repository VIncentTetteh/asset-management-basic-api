package com.assetiq.dto;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class AuditEventDto {
    private UUID    id;
    private UUID    organisationId;
    private UUID    actorId;
    private String  actorEmail;
    private String  method;
    private String  path;
    private String  query;
    private String  handler;
    private Integer responseStatus;
    private Boolean success;
    private String  message;
    private String  requestId;
    private String  clientIp;
    private String  userAgent;
    private Long    responseTimeMs;
    private Instant createdAt;

    // ── P4-B: structured classification fields ────────────────────────────────
    /** {@link com.assetiq.enums.AuditEventType} name as a string. */
    private String eventType;
    /** UUID (as string) of the affected resource (Role, User, …). */
    private String targetId;
    /** Before-state snapshot for RBAC change events. */
    private String oldValue;
    /** After-state snapshot for RBAC change events. */
    private String newValue;
}

