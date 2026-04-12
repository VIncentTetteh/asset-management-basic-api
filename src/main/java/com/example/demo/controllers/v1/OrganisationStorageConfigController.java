package com.example.demo.controllers.v1;

import com.example.demo.dto.OrganisationStorageConfigDto;
import com.example.demo.dto.OrganisationStorageConfigResponse;
import com.example.demo.multitenancy.TenantContext;
import com.example.demo.services.OrganisationStorageConfigService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST API for managing per-organisation S3 storage configuration.
 *
 * <p>All endpoints require ROLE_ADMIN or ROLE_ORG_ADMIN.
 *
 * <p>Base path: {@code /api/v1/organisations/{orgId}/storage-config}
 */
@RestController
@RequestMapping("/api/v1/organisations/{orgId}/storage-config")
public class OrganisationStorageConfigController {

    private final OrganisationStorageConfigService storageConfigService;

    public OrganisationStorageConfigController(OrganisationStorageConfigService storageConfigService) {
        this.storageConfigService = storageConfigService;
    }

    /**
     * GET — retrieve the current storage configuration.
     * Credentials are never stored, so the response is always safe to return.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN')")
    public ResponseEntity<OrganisationStorageConfigResponse> get(@PathVariable UUID orgId) {
        requireTenantAccess(orgId);
        return ResponseEntity.ok(storageConfigService.get(orgId));
    }

    /**
     * PUT — create or update the storage configuration (upsert).
     *
     * <p>Example: enable S3 with a custom bucket and 60-minute presign TTL:
     * <pre>
     * {
     *   "s3Enabled":      true,
     *   "bucketName":     "acme-assets",
     *   "reportPrefix":   "reports",
     *   "importPrefix":   "imports",
     *   "presignMinutes": 60
     * }
     * </pre>
     */
    @PutMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN')")
    public ResponseEntity<OrganisationStorageConfigResponse> upsert(
            @PathVariable UUID orgId,
            @Valid @RequestBody OrganisationStorageConfigDto dto) {
        requireTenantAccess(orgId);
        return ResponseEntity.ok(storageConfigService.upsert(orgId, dto));
    }

    /**
     * PATCH /toggle — enable or disable S3 without touching any other fields.
     *
     * <p>Request body: {@code { "enabled": true }}
     */
    @PatchMapping("/toggle")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN')")
    public ResponseEntity<OrganisationStorageConfigResponse> toggle(
            @PathVariable UUID orgId,
            @RequestBody Map<String, Boolean> body) {
        requireTenantAccess(orgId);
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(storageConfigService.toggleS3(orgId, enabled));
    }

    /**
     * DELETE — soft-delete the storage config.
     * The organisation reverts to in-memory storage until a new config is created.
     */
    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID orgId) {
        requireTenantAccess(orgId);
        storageConfigService.delete(orgId);
        return ResponseEntity.noContent().build();
    }

    // ── Security ──────────────────────────────────────────────────────────────

    private void requireTenantAccess(UUID orgId) {
        UUID tenantOrgId = TenantContext.getOrganisationId();
        // Platform admins (no tenant org) can access any org.
        // Org admins can only access their own org.
        if (tenantOrgId != null && !tenantOrgId.equals(orgId)) {
            throw new EntityNotFoundException("Organisation not found: " + orgId);
        }
    }
}
