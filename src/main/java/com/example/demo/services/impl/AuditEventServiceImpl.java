package com.example.demo.services.impl;

import com.example.demo.dto.AuditEventDto;
import com.example.demo.models.AuditEvent;
import com.example.demo.models.Organisation;
import com.example.demo.repositories.AuditEventRepository;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.services.AuditEventService;
import com.example.demo.services.TenantAwareService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AuditEventServiceImpl extends TenantAwareService implements AuditEventService {

    private final AuditEventRepository auditEventRepository;

    public AuditEventServiceImpl(AuditEventRepository auditEventRepository,
            OrganisationRepository organisationRepository) {
        super(organisationRepository);
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    public AuditEventDto getEventById(UUID id) {
        Organisation org = requireTenantOrg();
        AuditEvent event = auditEventRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Audit event not found"));
        return mapToDto(event);
    }

    @Override
    public List<AuditEventDto> getEvents(UUID actorId, Instant start, Instant end, Boolean success, String method, String path) {
        Organisation org = requireTenantOrg();
        String normalizedMethod = method != null ? method.toUpperCase(Locale.ROOT) : null;

        return auditEventRepository.findByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(org).stream()
                .filter(e -> actorId == null || (e.getActor() != null && actorId.equals(e.getActor().getId())))
                .filter(e -> start == null || (e.getCreatedAt() != null && !e.getCreatedAt().isBefore(start)))
                .filter(e -> end == null || (e.getCreatedAt() != null && !e.getCreatedAt().isAfter(end)))
                .filter(e -> success == null || success.equals(e.getSuccess()))
                .filter(e -> normalizedMethod == null || normalizedMethod.equalsIgnoreCase(e.getMethod()))
                .filter(e -> path == null || (e.getPath() != null && e.getPath().contains(path)))
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private AuditEventDto mapToDto(AuditEvent event) {
        AuditEventDto dto = new AuditEventDto();
        dto.setId(event.getId());
        dto.setOrganisationId(event.getOrganisation() != null ? event.getOrganisation().getId() : null);
        dto.setActorId(event.getActor() != null ? event.getActor().getId() : null);
        dto.setActorEmail(event.getActorEmail());
        dto.setMethod(event.getMethod());
        dto.setPath(event.getPath());
        dto.setQuery(event.getQuery());
        dto.setHandler(event.getHandler());
        dto.setResponseStatus(event.getResponseStatus());
        dto.setSuccess(event.getSuccess());
        dto.setMessage(event.getMessage());
        dto.setRequestId(event.getRequestId());
        dto.setClientIp(event.getClientIp());
        dto.setUserAgent(event.getUserAgent());
        dto.setResponseTimeMs(event.getResponseTimeMs());
        dto.setCreatedAt(event.getCreatedAt());
        return dto;
    }
}

