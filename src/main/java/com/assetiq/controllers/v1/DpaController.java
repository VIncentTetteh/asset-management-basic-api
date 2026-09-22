package com.assetiq.controllers.v1;

import com.assetiq.dpa.dto.ConsentRecordDto;
import com.assetiq.dpa.dto.CreateConsentRequest;
import com.assetiq.dpa.dto.CreateDsarRequest;
import com.assetiq.dpa.dto.DsarRequestDto;
import com.assetiq.dpa.dto.UpdateDsarStatusRequest;
import jakarta.servlet.http.HttpServletRequest;
import com.assetiq.dpa.model.DsarRequest;
import com.assetiq.dpa.service.DpaService;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST API for Ghana Data Protection Act 2012 compliance.
 *
 * Exposes two concerns:
 *  - /api/v1/dpa/consent  — consent recording and revocation
 *  - /api/v1/dpa/dsar     — Data Subject Access Request lifecycle
 *
 * All endpoints require an authenticated session. The currentUser and currentOrg
 * request attributes are populated by the tenant + JWT filter chain.
 */
@RestController
@RequestMapping("/api/v1/dpa")
@RequiredArgsConstructor
public class DpaController {

    private final DpaService dpaService;
    private final OrganisationRepository organisationRepository;
    private final UserRepository userRepository;
    private final com.assetiq.config.ClientIpResolver clientIpResolver;

    /*
     * Every endpoint used to take @RequestAttribute("currentUser"/"currentOrg"),
     * which nothing in the filter chain ever sets, so each DPA call failed with a
     * 400 "missing request attribute". The caller is resolved here from the
     * tenant context and the authenticated principal instead.
     */
    private Organisation currentOrg() {
        UUID orgId = TenantContext.getOrganisationId();
        if (orgId == null) {
            throw new AccessDeniedException("No organisation in the request context");
        }
        return organisationRepository.findByIdAndDeletedAtIsNull(orgId)
                .orElseThrow(() -> new AccessDeniedException("Organisation not found"));
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new AccessDeniedException("No authenticated user");
        }
        return userRepository.findByEmailAndOrganisationId(auth.getName(), currentOrg().getId())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user not found in organisation"));
    }

    // ── Consent ──────────────────────────────────────────────────────────────

    @PostMapping("/consent")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConsentRecordDto> recordConsent(
            @Valid @RequestBody CreateConsentRequest request,
            HttpServletRequest http) {
        // Evidence of the decision comes from the request, not the body: the
        // client could put any address there.
        return ResponseEntity.ok(dpaService.recordConsent(currentOrg(), currentUser(), request,
                clientIpResolver.resolve(http), http.getHeader("User-Agent")));
    }

    @DeleteMapping("/consent/{purpose}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConsentRecordDto> revokeConsent(
            @PathVariable String purpose) {
        return ResponseEntity.ok(dpaService.revokeConsent(currentOrg(), currentUser(), purpose));
    }

    @GetMapping("/consent")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_COMPLIANCE','MANAGE_COMPLIANCE')")
    public ResponseEntity<Page<ConsentRecordDto>> listConsents(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(dpaService.listConsents(currentOrg(), pageable));
    }

    @GetMapping("/consent/check")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Boolean> checkConsent(
            @RequestParam String purpose) {
        return ResponseEntity.ok(dpaService.hasActiveConsent(currentOrg(), currentUser(), purpose));
    }

    // ── DSAR ─────────────────────────────────────────────────────────────────

    @PostMapping("/dsar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DsarRequestDto> submitDsar(
            @Valid @RequestBody CreateDsarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dpaService.submitDsarRequest(currentOrg(), currentUser(), request));
    }

    @GetMapping("/dsar")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_COMPLIANCE','MANAGE_COMPLIANCE')")
    public ResponseEntity<Page<DsarRequestDto>> listDsar(
            @RequestParam(required = false) DsarRequest.Status status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(dpaService.listDsarRequests(currentOrg(), status, pageable));
    }

    @GetMapping("/dsar/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_COMPLIANCE','MANAGE_COMPLIANCE')")
    public ResponseEntity<DsarRequestDto> getDsar(
            @PathVariable UUID id) {
        return ResponseEntity.ok(dpaService.getDsarRequest(id, currentOrg()));
    }

    @PatchMapping("/dsar/{id}/status")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_COMPLIANCE')")
    public ResponseEntity<DsarRequestDto> updateDsarStatus(
            @PathVariable UUID id,
            // Query parameters are the legacy form, still accepted; the JSON body wins.
            @RequestParam(required = false) DsarRequest.Status status,
            @RequestParam(required = false) String responseSummary,
            @RequestParam(required = false) UUID assignedToUserId,
            @Valid @RequestBody(required = false) UpdateDsarStatusRequest body) {
        DsarRequest.Status newStatus = body != null && body.status() != null ? body.status() : status;
        String summary = body != null && body.responseSummary() != null ? body.responseSummary() : responseSummary;
        UUID assignee = body != null && body.assignedToUserId() != null ? body.assignedToUserId() : assignedToUserId;
        boolean clearAssignee = body != null && Boolean.TRUE.equals(body.clearAssignee());
        return ResponseEntity.ok(
                dpaService.updateDsarStatus(id, currentOrg(), newStatus, summary, assignee, clearAssignee));
    }
}
