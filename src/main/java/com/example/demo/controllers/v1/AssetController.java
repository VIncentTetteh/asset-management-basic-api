package com.example.demo.controllers.v1;

import com.example.demo.dto.AssetDto;
import com.example.demo.enums.AssetStatus;
import com.example.demo.services.AssetService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN')")
    public ResponseEntity<AssetDto> create(@Valid @RequestBody AssetDto dto) {
        try {
            AssetDto created = assetService.create(dto);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(null);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<AssetDto> get(@PathVariable UUID id) {
        AssetDto dto = assetService.get(id);
        if (dto == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(dto);
    }

    /**
     * List assets with optional filters.
     * Query params (mutually exclusive, first match wins):
     * ?status=IN_USE – filter by AssetStatus
     * ?departmentId=<uuid> – filter by department
     * ?categoryId=<uuid> – filter by category
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<?> list(
            @RequestParam(required = false) AssetStatus status,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID categoryId) {

        if (status != null) {
            Set<AssetDto> result = assetService.listByStatus(status);
            return ResponseEntity.ok(result);
        } else if (departmentId != null) {
            Set<AssetDto> result = assetService.listByDepartment(departmentId);
            return ResponseEntity.ok(result);
        } else if (categoryId != null) {
            Set<AssetDto> result = assetService.listByCategory(categoryId);
            return ResponseEntity.ok(result);
        }
        List<AssetDto> all = assetService.list();
        return ResponseEntity.ok(all);
    }

    @PostMapping("/{id}/assign/{departmentId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<AssetDto> assign(@PathVariable UUID id, @PathVariable UUID departmentId) {
        try {
            AssetDto dto = assetService.assignToDepartment(id, departmentId);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(null);
        }
    }

    @PostMapping("/{id}/assign-user/{userId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<AssetDto> assignUser(@PathVariable UUID id, @PathVariable UUID userId) {
        try {
            AssetDto dto = assetService.assignToUser(id, userId);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(null);
        }
    }

    @DeleteMapping("/{id}/assign-user")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<AssetDto> unassignUser(@PathVariable UUID id) {
        try {
            AssetDto dto = assetService.unassignUser(id);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<AssetDto> update(@PathVariable UUID id, @Valid @RequestBody AssetDto dto) {
        try {
            AssetDto updated = assetService.update(id, dto);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<AssetDto> patch(@PathVariable UUID id, @RequestBody AssetDto dto) {
        try {
            AssetDto updated = assetService.patch(id, dto);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        assetService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
