package com.assetiq.services;

import com.assetiq.dto.AuditEventDto;
import com.assetiq.dto.PagedResponseDto;
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

    /**
     * Same filters as {@link #getEvents}, one page at a time. The unpaged variant
     * loaded every matching row into memory, which is unbounded for a busy tenant.
     *
     * @param page 0-based page index; negative is treated as 0
     * @param size rows per page; clamped to {@value #MAX_PAGE_SIZE}
     */
    PagedResponseDto<AuditEventDto> getEventsPaged(UUID actorId, Instant start, Instant end,
                                                   Boolean success, String method, String path,
                                                   AuditEventType eventType, Integer page, Integer size);

    /** Upper bound on rows a single audit-event page may return. */
    int MAX_PAGE_SIZE = 200;

    /** Rows per page when the caller does not ask for a size. */
    int DEFAULT_PAGE_SIZE = 50;
}

