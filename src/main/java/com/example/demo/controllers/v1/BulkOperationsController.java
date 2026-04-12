package com.example.demo.controllers.v1;

import com.example.demo.dto.AssetImportResultDto;
import com.example.demo.models.IdempotencyRecord;
import com.example.demo.models.Organisation;
import com.example.demo.multitenancy.TenantContext;
import com.example.demo.services.AssetImportService;
import com.example.demo.services.impl.ReportGeneratorService;
import com.example.demo.repositories.IdempotencyRecordRepository;
import com.example.demo.repositories.OrganisationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Bulk Operations Controller
 * Handles bulk import/export of assets.
 */
@RestController
@RequestMapping("/api/v1/bulk")
public class BulkOperationsController {

    private static final Logger log = LoggerFactory.getLogger(BulkOperationsController.class);

    private final AssetImportService assetImportService;
    private final ReportGeneratorService reportGeneratorService;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;
    private final OrganisationRepository organisationRepository;

    public BulkOperationsController(AssetImportService assetImportService,
                                    ReportGeneratorService reportGeneratorService,
                                    IdempotencyRecordRepository idempotencyRecordRepository,
                                    ObjectMapper objectMapper,
                                    OrganisationRepository organisationRepository) {
        this.assetImportService = assetImportService;
        this.reportGeneratorService = reportGeneratorService;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.objectMapper = objectMapper;
        this.organisationRepository = organisationRepository;
    }

    /**
     * POST /api/v1/bulk/assets/import
     * Accepts an .xlsx file and bulk-imports assets.
     * Pass ?dryRun=true to validate without persisting.
     */
    @PostMapping(value = "/assets/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','EDIT_ASSET','DELETE_ASSET','MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<AssetImportResultDto> bulkImportAssets(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean dryRun) {

        log.info("[BulkImport] Received file: {}, dryRun={}", file.getOriginalFilename(), dryRun);

        Organisation org = requireTenantOrg();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            AssetImportResultDto result = assetImportService.importFromExcel(file, dryRun);
            return ResponseEntity.ok(result);
        }

        String trimmedKey = idempotencyKey.trim();
        String operation = "bulk/assets/import";

        byte[] fileBytes;
        String filename = file.getOriginalFilename();
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read uploaded file");
        }

        String cleanName = filename == null ? "" : filename.replaceAll("[^A-Za-z0-9._-]", "_");
        String contentType = file.getContentType();
        String requestHash = computeRequestHash(fileBytes, dryRun, cleanName, contentType);

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
                    // corrupted cached response: fall through to re-run
                }
            }
        }

        AssetImportResultDto result = assetImportService.importFromExcel(file, dryRun);
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
            idempotencyRecordRepository.save(rec);
        } catch (DataIntegrityViolationException e) {
            IdempotencyRecord rec = idempotencyRecordRepository.findByOrganisationAndOperationAndIdempotencyKeyAndDeletedAtIsNull(
                    org, operation, trimmedKey
            ).orElseThrow(() -> e);

            if (!rec.getRequestHash().equals(requestHash)) {
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

    /**
     * POST /api/v1/bulk/assets/export
     * Export all assets as CSV, EXCEL, or PDF.
     * Body: { "format": "CSV" | "EXCEL" | "PDF" }
     */
    @PostMapping("/assets/export")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','EDIT_ASSET','DELETE_ASSET','MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<byte[]> bulkExportAssets(@RequestBody Map<String, Object> request) {
        String format = ((String) request.getOrDefault("format", "CSV")).toUpperCase();
        try {
            UUID reportId = reportGeneratorService.generateAssetReport(format);
            ReportGeneratorService.ReportEntry entry = reportGeneratorService.get(reportId);
            if (entry == null) {
                return ResponseEntity.internalServerError().build();
            }
            return reportGeneratorService.download(reportId)
                    .map(obj -> ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + entry.filename() + "\"")
                            .contentType(MediaType.parseMediaType(entry.contentType()))
                            .body(obj.bytes()))
                    .orElseGet(() -> ResponseEntity.internalServerError().build());
        } catch (IOException e) {
            log.error("[BulkExport] Failed to generate asset export", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * POST /api/v1/bulk/purchase-orders/export
     * Export purchase orders as EXCEL or CSV.
     */
    @PostMapping("/purchase-orders/export")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','EDIT_ASSET','DELETE_ASSET','MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<byte[]> bulkExportPurchaseOrders(@RequestBody Map<String, Object> request) {
        String format = ((String) request.getOrDefault("format", "EXCEL")).toUpperCase();
        try {
            UUID reportId = reportGeneratorService.generatePurchaseOrderReport(format);
            ReportGeneratorService.ReportEntry entry = reportGeneratorService.get(reportId);
            if (entry == null) {
                return ResponseEntity.internalServerError().build();
            }
            return reportGeneratorService.download(reportId)
                    .map(obj -> ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + entry.filename() + "\"")
                            .contentType(MediaType.parseMediaType(entry.contentType()))
                            .body(obj.bytes()))
                    .orElseGet(() -> ResponseEntity.internalServerError().build());
        } catch (IOException e) {
            log.error("[BulkExport] Failed to generate PO export", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * POST /api/v1/bulk/suppliers/export
     */
    @PostMapping("/suppliers/export")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','EDIT_ASSET','DELETE_ASSET','MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<byte[]> bulkExportSuppliers(@RequestBody Map<String, Object> request) {
        String format = ((String) request.getOrDefault("format", "CSV")).toUpperCase();
        try {
            UUID reportId = reportGeneratorService.generateSupplierReport(format);
            ReportGeneratorService.ReportEntry entry = reportGeneratorService.get(reportId);
            if (entry == null) {
                return ResponseEntity.internalServerError().build();
            }
            return reportGeneratorService.download(reportId)
                    .map(obj -> ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + entry.filename() + "\"")
                            .contentType(MediaType.parseMediaType(entry.contentType()))
                            .body(obj.bytes()))
                    .orElseGet(() -> ResponseEntity.internalServerError().build());
        } catch (IOException e) {
            log.error("[BulkExport] Failed to generate supplier export", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
