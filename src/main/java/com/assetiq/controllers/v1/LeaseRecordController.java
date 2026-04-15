package com.assetiq.controllers.v1;

import com.assetiq.dto.LeaseRecordDto;
import com.assetiq.services.LeaseRecordService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leases")
public class LeaseRecordController {

    private final LeaseRecordService leaseRecordService;

    public LeaseRecordController(LeaseRecordService leaseRecordService) {
        this.leaseRecordService = leaseRecordService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','MANAGE_LEASES','VIEW_REPORTS')")
    public ResponseEntity<LeaseRecordDto> create(@Valid @RequestBody LeaseRecordDto dto) {
        return ResponseEntity.ok(leaseRecordService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','MANAGE_LEASES','VIEW_REPORTS')")
    public ResponseEntity<LeaseRecordDto> update(@PathVariable UUID id, @RequestBody LeaseRecordDto dto) {
        try {
            return ResponseEntity.ok(leaseRecordService.update(id, dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','ROLE_USER')")
    public ResponseEntity<LeaseRecordDto> getById(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(leaseRecordService.getById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','ROLE_USER')")
    public ResponseEntity<List<LeaseRecordDto>> listAll() {
        return ResponseEntity.ok(leaseRecordService.listAll());
    }

    @GetMapping("/assets/{assetId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','ROLE_USER')")
    public ResponseEntity<List<LeaseRecordDto>> listByAsset(@PathVariable UUID assetId) {
        return ResponseEntity.ok(leaseRecordService.listByAsset(assetId));
    }

    /**
     * Returns ACTIVE leases expiring within the next N days (default 30).
     * Example: GET /api/v1/leases/expiring-soon?days=60
     */
    @GetMapping("/expiring-soon")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','MANAGE_LEASES','VIEW_REPORTS')")
    public ResponseEntity<List<LeaseRecordDto>> listExpiringSoon(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(leaseRecordService.listExpiringSoon(days));
    }

    /** Terminate a lease early. */
    @PostMapping("/{id}/terminate")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','MANAGE_LEASES','VIEW_REPORTS')")
    public ResponseEntity<LeaseRecordDto> terminate(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        try {
            return ResponseEntity.ok(leaseRecordService.terminate(id, reason));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','MANAGE_LEASES','VIEW_REPORTS')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        leaseRecordService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
