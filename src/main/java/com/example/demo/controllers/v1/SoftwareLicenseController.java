package com.example.demo.controllers.v1;

import com.example.demo.dto.SoftwareLicenseDto;
import com.example.demo.services.SoftwareLicenseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/licenses")
public class SoftwareLicenseController {

    private final SoftwareLicenseService licenseService;

    public SoftwareLicenseController(SoftwareLicenseService licenseService) {
        this.licenseService = licenseService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','MANAGE_SOFTWARE_LICENSES')")
    public ResponseEntity<SoftwareLicenseDto> create(@Valid @RequestBody SoftwareLicenseDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(licenseService.create(dto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS','VIEW_SOFTWARE_LICENSES','MANAGE_SOFTWARE_LICENSES')")
    public ResponseEntity<SoftwareLicenseDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(licenseService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS','VIEW_SOFTWARE_LICENSES','MANAGE_SOFTWARE_LICENSES')")
    public ResponseEntity<List<SoftwareLicenseDto>> list() {
        return ResponseEntity.ok(licenseService.listAll());
    }

    /**
     * Returns licenses expiring within {@code days} days (default 30).
     * GET /api/v1/licenses/expiring-soon?days=60
     */
    @GetMapping("/expiring-soon")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS','VIEW_SOFTWARE_LICENSES','MANAGE_SOFTWARE_LICENSES')")
    public ResponseEntity<List<SoftwareLicenseDto>> expiringSoon(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(licenseService.listExpiringSoon(days));
    }

    /**
     * Returns licenses where used seats >= total seats.
     * GET /api/v1/licenses/over-allocated
     */
    @GetMapping("/over-allocated")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','VIEW_SOFTWARE_LICENSES','MANAGE_SOFTWARE_LICENSES')")
    public ResponseEntity<List<SoftwareLicenseDto>> overAllocated() {
        return ResponseEntity.ok(licenseService.listOverAllocated());
    }

    /**
     * Org-level utilisation summary.
     * GET /api/v1/licenses/utilization
     */
    @GetMapping("/utilization")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','VIEW_SOFTWARE_LICENSES','MANAGE_SOFTWARE_LICENSES')")
    public ResponseEntity<Map<String, Object>> utilization() {
        return ResponseEntity.ok(licenseService.getUtilizationSummary());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','MANAGE_SOFTWARE_LICENSES')")
    public ResponseEntity<SoftwareLicenseDto> update(@PathVariable UUID id,
                                                      @Valid @RequestBody SoftwareLicenseDto dto) {
        return ResponseEntity.ok(licenseService.update(id, dto));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','MANAGE_SOFTWARE_LICENSES')")
    public ResponseEntity<SoftwareLicenseDto> patch(@PathVariable UUID id,
                                                     @RequestBody SoftwareLicenseDto dto) {
        return ResponseEntity.ok(licenseService.patch(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','MANAGE_SOFTWARE_LICENSES')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        licenseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
