package com.assetiq.controllers.v1;

import com.assetiq.dto.AssetAuditDto;
import com.assetiq.dto.AuditItemDiscrepancyRequest;
import com.assetiq.dto.AuditItemDto;
import com.assetiq.dto.AuditItemVerifyRequest;
import com.assetiq.dto.AuditRemarksRequest;
import com.assetiq.dto.PagedResponseDto;
import com.assetiq.enums.AuditDiscrepancyType;
import com.assetiq.enums.AuditItemStatus;
import com.assetiq.enums.AuditStatus;
import com.assetiq.services.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audits")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','CONDUCT_AUDIT')")
    public ResponseEntity<AssetAuditDto> createAudit(@Valid @RequestBody AssetAuditDto auditDto) {
        AssetAuditDto createdAudit = auditService.createAudit(auditDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdAudit);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_AUDIT_LOGS')")
    public ResponseEntity<AssetAuditDto> getAuditById(@PathVariable UUID id) {
        AssetAuditDto audit = auditService.getAuditById(id);
        return ResponseEntity.ok(audit);
    }

    /**
     * The organisation's audits, newest first. Every filter is optional and they
     * combine (AND): department, audit-date range (either end may be open),
     * auditor and status.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_AUDIT_LOGS')")
    public ResponseEntity<List<AssetAuditDto>> getAudits(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) UUID conductedById,
            @RequestParam(required = false) AuditStatus status) {
        return ResponseEntity.ok(auditService.searchAudits(departmentId, startDate, endDate, conductedById, status));
    }

    /** Updates an open audit's remarks (status changes go through PATCH /status). */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','CONDUCT_AUDIT')")
    public ResponseEntity<AssetAuditDto> updateAuditRemarks(@PathVariable UUID id,
            @Valid @RequestBody AuditRemarksRequest request) {
        return ResponseEntity.ok(auditService.updateAuditRemarks(id, request.remarks()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','CONDUCT_AUDIT')")
    public ResponseEntity<AssetAuditDto> updateAuditStatus(@PathVariable UUID id,
            @RequestParam String status) {
        AssetAuditDto updatedAudit = auditService.updateAuditStatus(id, status);
        return ResponseEntity.ok(updatedAudit);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','CONDUCT_AUDIT')")
    public ResponseEntity<Void> deleteAudit(@PathVariable UUID id) {
        auditService.deleteAudit(id);
        return ResponseEntity.noContent().build();
    }

    // ── Count sheet ──────────────────────────────────────────────────────────

    /**
     * Builds (or tops up) the audit's count sheet: one item per in-scope asset.
     * Starting an audit does this automatically; this endpoint is for picking up
     * assets added after it started, and for an audit that was created already
     * IN_PROGRESS.
     */
    @PostMapping("/{id}/items/generate")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','CONDUCT_AUDIT')")
    public ResponseEntity<AssetAuditDto> generateAuditItems(@PathVariable UUID id) {
        return ResponseEntity.ok(auditService.generateAuditItems(id));
    }

    /**
     * One page of the audit's items. Every filter is optional and they combine
     * (AND); {@code search} matches asset tag or name. Page size defaults to 20 and
     * is capped at 200.
     */
    @GetMapping("/{id}/items")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_AUDIT_LOGS','CONDUCT_AUDIT')")
    public ResponseEntity<PagedResponseDto<AuditItemDto>> listAuditItems(
            @PathVariable UUID id,
            @RequestParam(required = false) AuditItemStatus status,
            @RequestParam(required = false) AuditDiscrepancyType discrepancyType,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(auditService.listAuditItems(id, status, discrepancyType, search, page, size));
    }

    /**
     * Records that an asset was sighted. The body's {@code scan} is whatever came
     * off the label — a QR link, the legacy {@code asset:<uuid>} text, a bare id, or
     * an asset tag typed by hand. An asset that is not on the sheet is recorded as
     * an UNEXPECTED discrepancy rather than silently verified.
     */
    @PostMapping("/{id}/items/verify")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','CONDUCT_AUDIT')")
    public ResponseEntity<AuditItemDto> verifyAuditItem(@PathVariable UUID id,
            @Valid @RequestBody AuditItemVerifyRequest request) {
        return ResponseEntity.ok(auditService.verifyAuditItem(id, request));
    }

    /** Flags one item as wrong: a type (MISSING, WRONG_LOCATION, UNEXPECTED, DAMAGED) and a reason. */
    @PostMapping("/{id}/items/{itemId}/discrepancy")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','CONDUCT_AUDIT')")
    public ResponseEntity<AuditItemDto> flagAuditItemDiscrepancy(@PathVariable UUID id,
            @PathVariable UUID itemId,
            @Valid @RequestBody AuditItemDiscrepancyRequest request) {
        return ResponseEntity.ok(auditService.flagAuditItemDiscrepancy(id, itemId, request));
    }
}
