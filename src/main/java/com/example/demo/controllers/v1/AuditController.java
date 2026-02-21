package com.example.demo.controllers.v1;

import com.example.demo.dto.AssetAuditDto;
import com.example.demo.services.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<AssetAuditDto> createAudit(@Valid @RequestBody AssetAuditDto auditDto) {
        AssetAuditDto createdAudit = auditService.createAudit(auditDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdAudit);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssetAuditDto> getAuditById(@PathVariable UUID id) {
        AssetAuditDto audit = auditService.getAuditById(id);
        return ResponseEntity.ok(audit);
    }

    @GetMapping
    public ResponseEntity<Set<AssetAuditDto>> getAudits(
            @RequestParam(required = false) UUID organisationId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) UUID conductedById) {

        if (organisationId != null) {
            return ResponseEntity.ok(auditService.getAuditsByOrganisation(organisationId));
        } else if (departmentId != null) {
            return ResponseEntity.ok(auditService.getAuditsByDepartment(departmentId));
        } else if (startDate != null && endDate != null) {
            return ResponseEntity.ok(auditService.getAuditsByDateRange(startDate, endDate));
        } else if (conductedById != null) {
            return ResponseEntity.ok(auditService.getAuditsByConductor(conductedById));
        }
        return ResponseEntity.badRequest().build();
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<AssetAuditDto> updateAuditStatus(@PathVariable UUID id,
                                                          @RequestParam String status) {
        AssetAuditDto updatedAudit = auditService.updateAuditStatus(id, status);
        return ResponseEntity.ok(updatedAudit);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAudit(@PathVariable UUID id) {
        auditService.deleteAudit(id);
        return ResponseEntity.noContent().build();
    }
}

