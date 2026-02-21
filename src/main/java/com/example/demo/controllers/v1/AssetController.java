package com.example.demo.controllers.v1;

import com.example.demo.dto.AssetDto;
import com.example.demo.services.AssetService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ORG_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<AssetDto> create(@Valid @RequestBody AssetDto dto) {
        AssetDto created = assetService.create(dto);
        return ResponseEntity.ok(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<AssetDto> get(@PathVariable UUID id) {
        AssetDto dto = assetService.get(id);
        if (dto == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(dto);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<List<AssetDto>> list() {
        return ResponseEntity.ok(assetService.list());
    }

    @PostMapping("/{id}/assign/{departmentId}")
    @PreAuthorize("hasAuthority('ROLE_ORG_ADMIN') or hasAuthority('ROLE_ADMIN')")
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

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ORG_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<AssetDto> update(@PathVariable UUID id, @Valid @RequestBody AssetDto dto) {
        try {
            AssetDto updated = assetService.update(id, dto);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ORG_ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        assetService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
