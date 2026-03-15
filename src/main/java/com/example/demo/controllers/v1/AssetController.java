package com.example.demo.controllers.v1;

import com.example.demo.dto.AssetDto;
import com.example.demo.dto.AssetHistoryEventDto;
import com.example.demo.dto.AssetImportResultDto;
import com.example.demo.enums.AssetStatus;
import com.example.demo.security.TenantAuthorizationService;
import com.example.demo.services.AssetImportService;
import com.example.demo.services.AssetService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetService assetService;
    private final AssetImportService assetImportService;
    private final TenantAuthorizationService tenantAuthorizationService;

    public AssetController(AssetService assetService, AssetImportService assetImportService,
                          TenantAuthorizationService tenantAuthorizationService) {
        this.assetService = assetService;
        this.assetImportService = assetImportService;
        this.tenantAuthorizationService = tenantAuthorizationService;
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
    @PreAuthorize("@tenantAuthorizationService.isAssetAccessible(#id)")
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

    /**
     * Generates a QR code PNG for the given asset.
     * The QR payload encodes the asset UUID for scanner lookup.
     * GET /api/v1/assets/{id}/qrcode
     */
    @GetMapping(value = "/{id}/qrcode", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER')")
    public ResponseEntity<byte[]> getQrCode(@PathVariable UUID id) {
        AssetDto asset = assetService.get(id);
        if (asset == null) return ResponseEntity.notFound().build();

        try {
            String payload = "asset:" + id;
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(payload, BarcodeFormat.QR_CODE, 300, 300);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"asset-" + id + ".png\"");
            return ResponseEntity.ok().headers(headers).body(out.toByteArray());
        } catch (WriterException | IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Unified chronological history for an asset.
     * Aggregates API audit events, transfers, maintenance records, and disposals.
     * GET /api/v1/assets/{id}/history
     */
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER')")
    public ResponseEntity<List<AssetHistoryEventDto>> getHistory(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(assetService.getHistory(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Bulk-import assets from an Excel file (.xlsx).
     * <p>
     * Expected sheet columns (row 1 = header, data starts at row 2):
     * name | assetTag | serialNumber | description | assetType | manufacturer | model |
     * purchaseDate | purchaseCost | currency | depreciationMethod | usefulLifeMonths |
     * residualValue | warrantyExpiryDate | status | condition | categoryId | locationId |
     * supplierId | departmentId | assignedUserId | invoiceId | insurancePolicyId
     * </p>
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN')")
    public ResponseEntity<AssetImportResultDto> importAssets(
            @RequestParam("file") MultipartFile file) {
        AssetImportResultDto result = assetImportService.importFromExcel(file);
        return ResponseEntity.ok(result);
    }
}
