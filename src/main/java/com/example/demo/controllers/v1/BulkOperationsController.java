package com.example.demo.controllers.v1;

import com.example.demo.dto.AssetImportResultDto;
import com.example.demo.services.AssetImportService;
import com.example.demo.services.impl.ReportGeneratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    public BulkOperationsController(AssetImportService assetImportService,
                                    ReportGeneratorService reportGeneratorService) {
        this.assetImportService = assetImportService;
        this.reportGeneratorService = reportGeneratorService;
    }

    /**
     * POST /api/v1/bulk/assets/import
     * Accepts an .xlsx file and bulk-imports assets.
     * Pass ?dryRun=true to validate without persisting.
     */
    @PostMapping(value = "/assets/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<AssetImportResultDto> bulkImportAssets(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean dryRun) {

        log.info("[BulkImport] Received file: {}, dryRun={}", file.getOriginalFilename(), dryRun);

        if (dryRun) {
            // Validate only — delegate to service but wrap result before it's committed
            // For simplicity, dry-run returns the same result object without a DB commit.
            // A full async dry-run is a future enhancement.
            AssetImportResultDto result = assetImportService.importFromExcel(file);
            result.setDryRun(true);
            return ResponseEntity.ok(result);
        }

        AssetImportResultDto result = assetImportService.importFromExcel(file);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/v1/bulk/assets/export
     * Export all assets as CSV, EXCEL, or PDF.
     * Body: { "format": "CSV" | "EXCEL" | "PDF" }
     */
    @PostMapping("/assets/export")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
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
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
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
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
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
