package com.assetiq.controllers.v1;

import com.assetiq.dto.AssetDto;
import com.assetiq.dto.AssetFilterRequest;
import com.assetiq.dto.AssetHistoryEventDto;
import com.assetiq.dto.AssetImportResultDto;
import com.assetiq.dto.AssetStatsDto;
import com.assetiq.dto.PagedResponseDto;
import com.assetiq.dto.TcoDto;
import com.assetiq.models.IdempotencyRecord;
import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.security.TenantAuthorizationService;
import com.assetiq.services.AssetImportService;
import com.assetiq.services.AssetService;
import com.assetiq.repositories.IdempotencyRecordRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetService assetService;
    private final AssetImportService assetImportService;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;
    private final OrganisationRepository organisationRepository;

    public AssetController(AssetService assetService, AssetImportService assetImportService,
                          TenantAuthorizationService tenantAuthorizationService,
                          IdempotencyRecordRepository idempotencyRecordRepository,
                          ObjectMapper objectMapper,
                          OrganisationRepository organisationRepository) {
        this.assetService = assetService;
        this.assetImportService = assetImportService;
        this.tenantAuthorizationService = tenantAuthorizationService;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.objectMapper = objectMapper;
        this.organisationRepository = organisationRepository;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','CREATE_ASSET','EDIT_ASSET')")
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
     * List assets with server-side combined filtering and pagination.
     *
     * GET /api/v1/assets?search=laptop&status=IN_USE&departmentId=uuid&page=0&size=20&sort=name,asc
     *
     * All parameters optional. Present parameters are AND-combined.
     * Sortable fields: name, assetTag, serialNumber, manufacturer, model,
     *                  purchaseCost, purchaseDate, createdAt, updatedAt, status, condition.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS')")
    public ResponseEntity<PagedResponseDto<AssetDto>> list(@ModelAttribute AssetFilterRequest req) {
        return ResponseEntity.ok(assetService.listPaged(req));
    }

    /**
     * Aggregate counts per status + assigned/unassigned totals for the current tenant.
     * Cheap GROUP BY query — safe to call on every page load.
     * GET /api/v1/assets/stats
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS')")
    public ResponseEntity<AssetStatsDto> getStats() {
        return ResponseEntity.ok(assetService.getStats());
    }

    @PostMapping("/{id}/assign/{departmentId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','DELETE_ASSET','EDIT_ASSET','TRANSFER_ASSET','DISPOSE_ASSET')")
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
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','DELETE_ASSET','EDIT_ASSET','TRANSFER_ASSET','DISPOSE_ASSET')")
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
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','DELETE_ASSET','EDIT_ASSET','TRANSFER_ASSET','DISPOSE_ASSET')")
    public ResponseEntity<AssetDto> unassignUser(@PathVariable UUID id) {
        try {
            AssetDto dto = assetService.unassignUser(id);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','DELETE_ASSET','EDIT_ASSET','TRANSFER_ASSET','DISPOSE_ASSET')")
    public ResponseEntity<AssetDto> update(@PathVariable UUID id, @Valid @RequestBody AssetDto dto) {
        try {
            AssetDto updated = assetService.update(id, dto);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','DELETE_ASSET','EDIT_ASSET','TRANSFER_ASSET','DISPOSE_ASSET')")
    public ResponseEntity<AssetDto> patch(@PathVariable UUID id, @RequestBody AssetDto dto) {
        try {
            AssetDto updated = assetService.patch(id, dto);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','DELETE_ASSET','EDIT_ASSET','TRANSFER_ASSET','DISPOSE_ASSET')")
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
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS')")
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
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS')")
    public ResponseEntity<List<AssetHistoryEventDto>> getHistory(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(assetService.getHistory(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Calculate the Total Cost of Ownership for an asset.
     * GET /api/v1/assets/{id}/tco
     */
    @GetMapping("/{id}/tco")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS')")
    public ResponseEntity<TcoDto> getTco(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(assetService.getTco(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Look up an asset by the decoded QR payload (e.g. "asset:<uuid>").
     * Useful for scanner applications that decode the QR and call this endpoint.
     * GET /api/v1/assets/scan/{payload}
     */
    @GetMapping("/scan/{payload}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS')")
    public ResponseEntity<AssetDto> getByQrPayload(@PathVariable String payload) {
        try {
            return ResponseEntity.ok(assetService.getByQrPayload(payload));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Generate an enriched QR payload JSON for an asset, including org context.
     * Returns a JSON string suitable for encoding in a QR code.
     * GET /api/v1/assets/{id}/qrcode/payload
     */
    @GetMapping(value = "/{id}/qrcode/payload", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS')")
    public ResponseEntity<java.util.Map<String, Object>> getQrPayload(@PathVariable UUID id) {
        AssetDto asset = assetService.get(id);
        if (asset == null) return ResponseEntity.notFound().build();
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("type", "asset");
        payload.put("id", id.toString());
        payload.put("assetTag", asset.getAssetTag());
        payload.put("name", asset.getName());
        payload.put("orgId", asset.getOrganisationId() != null ? asset.getOrganisationId().toString() : null);
        return ResponseEntity.ok(payload);
    }

    /**
     * Bulk-import assets from an Excel file (.xlsx).
     * <p>
     * Expected sheet columns (row 1 = header, data starts at row 2):
     * name | assetTag | serialNumber | description | assetType | manufacturer | model |
     * purchaseDate | purchaseCost | currency | depreciationMethod | usefulLifeMonths |
     * residualValue | warrantyExpiryDate | status | condition | categoryId | locationId |
     * supplierId | departmentId | assignedUserId | invoiceId | insurancePolicyId
     * Any columns appended after the standard template are persisted as asset custom fields.
     * </p>
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','CREATE_ASSET','EDIT_ASSET')")
    public ResponseEntity<AssetImportResultDto> importAssets(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam("file") MultipartFile file) {
        Organisation org = requireTenantOrg();

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            AssetImportResultDto result = assetImportService.importFromExcel(file);
            return ResponseEntity.ok(result);
        }

        String trimmedKey = idempotencyKey.trim();
        String operation = "assets/import";

        byte[] fileBytes;
        String filename = file.getOriginalFilename();
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read uploaded file");
        }

        String cleanName = filename == null ? "" : filename.replaceAll("[^A-Za-z0-9._-]", "_");
        String contentType = file.getContentType();
        String requestHash = computeRequestHash(fileBytes, false, cleanName, contentType);

        var existing = idempotencyRecordRepository.findByOrganisationAndOperationAndIdempotencyKeyAndDeletedAtIsNull(
                org, operation, trimmedKey
        );
        if (existing.isPresent()) {
            IdempotencyRecord rec = existing.get();
            if (!rec.getRequestHash().equals(requestHash)) {
                throw new IllegalStateException("Idempotency key already used with a different request payload");
            }
            if (rec.getResponseJson() != null) {
                try {
                    AssetImportResultDto cached = objectMapper.readValue(rec.getResponseJson(), AssetImportResultDto.class);
                    return ResponseEntity.ok(cached);
                } catch (JsonProcessingException e) {
                    // Fall through to re-run import if cached response is corrupted.
                }
            }
        }

        AssetImportResultDto result = assetImportService.importFromExcel(file);
        String responseJson;
        try {
            responseJson = objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotent response", e);
        }

        try {
            IdempotencyRecord rec = new IdempotencyRecord();
            rec.setOrganisation(org);
            rec.setOperation(operation);
            rec.setIdempotencyKey(trimmedKey);
            rec.setRequestHash(requestHash);
            rec.setResponseJson(responseJson);
            // responseJobId is nullable for sync operations
            idempotencyRecordRepository.save(rec);
        } catch (DataIntegrityViolationException e) {
            // Another request stored the same key first.
            IdempotencyRecord rec = idempotencyRecordRepository.findByOrganisationAndOperationAndIdempotencyKeyAndDeletedAtIsNull(
                    org, operation, trimmedKey
            ).orElseThrow(() -> e);

            if (rec.getRequestHash() != null && !rec.getRequestHash().equals(requestHash)) {
                throw new IllegalStateException("Idempotency key already used with a different request payload");
            }
            try {
                AssetImportResultDto cached = objectMapper.readValue(rec.getResponseJson(), AssetImportResultDto.class);
                return ResponseEntity.ok(cached);
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Failed to parse cached idempotent response", ex);
            }
        }

        return ResponseEntity.ok(result);
    }

    private Organisation requireTenantOrg() {
        UUID orgId = TenantContext.getOrganisationId();
        if (orgId == null) {
            throw new AccessDeniedException("Tenant context is required");
        }
        return organisationRepository.findByIdAndDeletedAtIsNull(orgId)
                .orElseThrow(() -> new AccessDeniedException("Organisation not found or inactive for current tenant"));
    }

    private static String computeRequestHash(byte[] fileBytes, boolean dryRun, String cleanName, String contentType) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((Boolean.toString(dryRun) + "|").getBytes(StandardCharsets.UTF_8));
            digest.update((cleanName == null ? "" : cleanName).getBytes(StandardCharsets.UTF_8));
            digest.update("|".getBytes(StandardCharsets.UTF_8));
            digest.update((contentType == null ? "" : contentType).getBytes(StandardCharsets.UTF_8));
            digest.update("|".getBytes(StandardCharsets.UTF_8));
            digest.update(fileBytes);
            return toHexLower(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String toHexLower(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
