package com.assetiq.controllers.v1;

import com.assetiq.dpa.dto.ConsentRecordDto;
import com.assetiq.dpa.dto.CreateConsentRequest;
import com.assetiq.dpa.dto.CreateDsarRequest;
import com.assetiq.dpa.dto.DsarRequestDto;
import com.assetiq.dpa.model.DsarRequest;
import com.assetiq.dpa.service.DpaService;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
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

    // ── Consent ──────────────────────────────────────────────────────────────

    @PostMapping("/consent")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConsentRecordDto> recordConsent(
            @RequestAttribute("currentUser") User user,
            @RequestAttribute("currentOrg")  Organisation org,
            @Valid @RequestBody CreateConsentRequest request) {
        return ResponseEntity.ok(dpaService.recordConsent(org, user, request));
    }

    @DeleteMapping("/consent/{purpose}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConsentRecordDto> revokeConsent(
            @RequestAttribute("currentUser") User user,
            @RequestAttribute("currentOrg")  Organisation org,
            @PathVariable String purpose) {
        return ResponseEntity.ok(dpaService.revokeConsent(org, user, purpose));
    }

    @GetMapping("/consent")
    @PreAuthorize("hasAuthority('DPA_VIEW') or hasAuthority('ORG_ADMIN')")
    public ResponseEntity<Page<ConsentRecordDto>> listConsents(
            @RequestAttribute("currentOrg")  Organisation org,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(dpaService.listConsents(org, pageable));
    }

    @GetMapping("/consent/check")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Boolean> checkConsent(
            @RequestAttribute("currentUser") User user,
            @RequestAttribute("currentOrg")  Organisation org,
            @RequestParam String purpose) {
        return ResponseEntity.ok(dpaService.hasActiveConsent(org, user, purpose));
    }

    // ── DSAR ─────────────────────────────────────────────────────────────────

    @PostMapping("/dsar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DsarRequestDto> submitDsar(
            @RequestAttribute("currentUser") User user,
            @RequestAttribute("currentOrg")  Organisation org,
            @Valid @RequestBody CreateDsarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dpaService.submitDsarRequest(org, user, request));
    }

    @GetMapping("/dsar")
    @PreAuthorize("hasAuthority('DPA_VIEW') or hasAuthority('ORG_ADMIN')")
    public ResponseEntity<Page<DsarRequestDto>> listDsar(
            @RequestAttribute("currentOrg")  Organisation org,
            @RequestParam(required = false) DsarRequest.Status status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(dpaService.listDsarRequests(org, status, pageable));
    }

    @GetMapping("/dsar/{id}")
    @PreAuthorize("hasAuthority('DPA_VIEW') or hasAuthority('ORG_ADMIN')")
    public ResponseEntity<DsarRequestDto> getDsar(
            @RequestAttribute("currentOrg") Organisation org,
            @PathVariable UUID id) {
        return ResponseEntity.ok(dpaService.getDsarRequest(id, org));
    }

    @PatchMapping("/dsar/{id}/status")
    @PreAuthorize("hasAuthority('DPA_MANAGE') or hasAuthority('ORG_ADMIN')")
    public ResponseEntity<DsarRequestDto> updateDsarStatus(
            @RequestAttribute("currentOrg") Organisation org,
            @PathVariable UUID id,
            @RequestParam DsarRequest.Status status,
            @RequestParam(required = false) String responseSummary,
            @RequestParam(required = false) UUID assignedToUserId) {
        return ResponseEntity.ok(
                dpaService.updateDsarStatus(id, org, status, responseSummary, assignedToUserId));
    }
}
