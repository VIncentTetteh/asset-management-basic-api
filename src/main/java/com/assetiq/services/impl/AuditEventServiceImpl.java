package com.assetiq.services.impl;

import com.assetiq.dto.AuditEventDto;
import com.assetiq.enums.AuditEventType;
import com.assetiq.models.AuditEvent;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.AuditEventRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.AuditEventService;
import com.assetiq.services.TenantAwareService;
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

    /**
     * P4-C: All filtering is now pushed to the database via a single parameterised
     * JPQL query.  The previous implementation loaded every event for the org and
     * filtered them in Java streams, which would OOM for a busy tenant.
     */
    @Override
    public List<AuditEventDto> getEvents(UUID actorId, Instant start, Instant end,
                                         Boolean success, String method, String path,
                                         AuditEventType eventType) {
        Organisation org = requireTenantOrg();
        // Normalise method to upper-case so UPPER(e.method) = UPPER(:method) matches
        String normMethod = method != null ? method.toUpperCase(Locale.ROOT) : null;

        return auditEventRepository
                .findFiltered(org, actorId, start, end, success, normMethod, path, eventType)
                .stream()
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
        // P4-B new fields
        dto.setEventType(event.getEventType() != null ? event.getEventType().name() : null);
        dto.setTargetId(event.getTargetId());
        dto.setOldValue(event.getOldValue());
        dto.setNewValue(event.getNewValue());
        return dto;
    }
}
