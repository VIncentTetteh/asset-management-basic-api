package com.assetiq.controllers.v1;

import com.assetiq.dto.AssetAuditDto;
import com.assetiq.services.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audits")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','CONDUCT_AUDIT','EXPORT_AUDIT_LOGS')")
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

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_AUDIT_LOGS')")
    public ResponseEntity<Set<AssetAuditDto>> getAudits(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) UUID conductedById) {

        if (departmentId != null) {
            return ResponseEntity.ok(auditService.getAuditsByDepartment(departmentId));
        } else if (startDate != null && endDate != null) {
            return ResponseEntity.ok(auditService.getAuditsByDateRange(startDate, endDate));
        } else if (conductedById != null) {
            return ResponseEntity.ok(auditService.getAuditsByConductor(conductedById));
        }
        // H8: No filter → return all for this tenant org
        return ResponseEntity.ok(auditService.getAuditsByOrganisation(null));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','CONDUCT_AUDIT','EXPORT_AUDIT_LOGS')")
    public ResponseEntity<AssetAuditDto> updateAuditStatus(@PathVariable UUID id,
            @RequestParam String status) {
        AssetAuditDto updatedAudit = auditService.updateAuditStatus(id, status);
        return ResponseEntity.ok(updatedAudit);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','CONDUCT_AUDIT','EXPORT_AUDIT_LOGS')")
    public ResponseEntity<Void> deleteAudit(@PathVariable UUID id) {
        auditService.deleteAudit(id);
        return ResponseEntity.noContent().build();
    }
}
