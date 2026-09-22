package com.assetiq.controllers.v1;

import com.assetiq.dto.AuditEventDto;
import com.assetiq.dto.PagedResponseDto;
import com.assetiq.enums.AuditEventType;
import com.assetiq.services.AuditEventService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditEventController {

    private final AuditEventService auditEventService;

    public AuditEventController(AuditEventService auditEventService) {
        this.auditEventService = auditEventService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_AUDIT_LOGS','EXPORT_AUDIT_LOGS')")
    public ResponseEntity<AuditEventDto> getEventById(@PathVariable UUID id) {
        return ResponseEntity.ok(auditEventService.getEventById(id));
    }

    /**
     * List audit events, one page at a time, with optional server-side filtering.
     * The list used to be unbounded, so a busy tenant returned every row it had.
     *
     * @param eventType optional {@link AuditEventType} name to restrict results
     *                  (e.g. {@code ROLE_PERMISSIONS_CHANGED}, {@code AUTH_FAILURE})
     * @param page      0-based page index (default 0)
     * @param size      rows per page (default 50, capped at 200)
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_AUDIT_LOGS','EXPORT_AUDIT_LOGS')")
    public ResponseEntity<PagedResponseDto<AuditEventDto>> getEvents(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) AuditEventType eventType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(auditEventService.getEventsPaged(
                actorId, start, end, success, method, path, eventType, page, size));
    }
}
