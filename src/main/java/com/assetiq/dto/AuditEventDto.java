package com.assetiq.dto;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class AuditEventDto {
    private UUID id;
    private UUID organisationId;
    private UUID actorId;
    private String actorEmail;
    private String method;
    private String path;
    private String query;
    private String handler;
    private Integer responseStatus;
    private Boolean success;
    private String message;
    private String requestId;
    private String clientIp;
    private String userAgent;
    private Long responseTimeMs;
    private Instant createdAt;
}

