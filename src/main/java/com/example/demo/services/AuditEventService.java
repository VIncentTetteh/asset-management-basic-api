package com.example.demo.services;

import com.example.demo.dto.AuditEventDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditEventService {
    AuditEventDto getEventById(UUID id);

    List<AuditEventDto> getEvents(UUID actorId, Instant start, Instant end, Boolean success, String method);
}

