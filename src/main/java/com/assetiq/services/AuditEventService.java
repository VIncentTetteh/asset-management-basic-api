package com.assetiq.services;

import com.assetiq.dto.AuditEventDto;
import com.assetiq.enums.AuditEventType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditEventService {
    AuditEventDto getEventById(UUID id);

    /**
     * Returns audit events for the current tenant, all parameters optional.
     * All filtering is performed at the database level (P4-C).
     *
     * @param eventType if non-null, restrict to events of this category
     */
    List<AuditEventDto> getEvents(UUID actorId, Instant start, Instant end,
                                  Boolean success, String method, String path,
                                  AuditEventType eventType);
}

