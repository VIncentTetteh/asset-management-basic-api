package com.assetiq.controllers.v1;

import com.assetiq.dto.AssetCustomFieldDto;
import com.assetiq.services.AssetCustomFieldService;
import com.assetiq.services.FeatureFlagGate;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Manages arbitrary key-value custom fields on assets.
 * All routes are scoped under /api/v1/assets/{assetId}/custom-fields.
 */
@RestController
@RequestMapping("/api/v1/assets/{assetId}/custom-fields")
@FeatureFlagGate("commercial.governed-custom-fields")
public class AssetCustomFieldController {

    private final AssetCustomFieldService fieldService;

    public AssetCustomFieldController(AssetCustomFieldService fieldService) {
        this.fieldService = fieldService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','EDIT_ASSET')")
    public ResponseEntity<AssetCustomFieldDto> create(
            @PathVariable UUID assetId,
            @Valid @RequestBody AssetCustomFieldDto dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(fieldService.create(assetId, dto));
        } catch (IllegalArgumentException e) {
            // Stays a 404, now with the reason in the body.
            throw new com.assetiq.exceptions.ResourceNotFoundException(e.getMessage());
        }
        // IllegalStateException (duplicate field name) -> 409 with its message.
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS')")
    public ResponseEntity<List<AssetCustomFieldDto>> list(@PathVariable UUID assetId) {
        try {
            return ResponseEntity.ok(fieldService.listByAsset(assetId));
        } catch (IllegalArgumentException e) {
            // Stays a 404, now with the reason in the body.
            throw new com.assetiq.exceptions.ResourceNotFoundException(e.getMessage());
        }
    }

    @PutMapping("/{fieldId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','EDIT_ASSET')")
    public ResponseEntity<AssetCustomFieldDto> update(
            @PathVariable UUID assetId,
            @PathVariable UUID fieldId,
            @Valid @RequestBody AssetCustomFieldDto dto) {
        try {
            return ResponseEntity.ok(fieldService.update(fieldId, dto));
        } catch (IllegalArgumentException e) {
            // Stays a 404, now with the reason in the body.
            throw new com.assetiq.exceptions.ResourceNotFoundException(e.getMessage());
        }
    }

    @DeleteMapping("/{fieldId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','EDIT_ASSET')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID assetId,
            @PathVariable UUID fieldId) {
        try {
            fieldService.delete(fieldId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            // Stays a 404, now with the reason in the body.
            throw new com.assetiq.exceptions.ResourceNotFoundException(e.getMessage());
        }
    }
}
