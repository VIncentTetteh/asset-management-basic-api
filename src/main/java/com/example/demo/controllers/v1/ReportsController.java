package com.example.demo.controllers.v1;

import com.example.demo.services.impl.ReportGeneratorService;
import com.example.demo.multitenancy.TenantContext;
import com.example.demo.models.ReportMetadata;
import com.example.demo.repositories.ReportMetadataRepository;
import com.example.demo.dto.PagedResponseDto;
import com.example.demo.dto.ReportHistoryItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.demo.storage.FileStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportsController {

    private static final Logger log = LoggerFactory.getLogger(ReportsController.class);

    private final ReportGeneratorService reportGeneratorService;
    private final ReportMetadataRepository reportMetadataRepository;
    private final FileStorageService storageService;

    public ReportsController(ReportGeneratorService reportGeneratorService,
                              ReportMetadataRepository reportMetadataRepository,
                              FileStorageService storageService) {
        this.reportGeneratorService = reportGeneratorService;
        this.reportMetadataRepository = reportMetadataRepository;
        this.storageService = storageService;
    }

    // ── Generate endpoints ────────────────────────────────────────────────────

    @PostMapping("/assets")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> generateAssetReport(@RequestBody Map<String, Object> request) {
        String format = formatOf(request);
        try {
            UUID reportId = reportGeneratorService.generateAssetReport(format);
            return ResponseEntity.status(HttpStatus.CREATED).body(reportMeta(reportId, "assets", format));
        } catch (IOException e) {
            log.error("Failed to generate asset report", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to generate report. Please try again later."));
        }
    }

    @PostMapping("/financial")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> generateFinancialReport(@RequestBody Map<String, Object> request) {
        String format = formatOf(request);
        try {
            UUID reportId = reportGeneratorService.generateFinancialReport(format);
            return ResponseEntity.status(HttpStatus.CREATED).body(reportMeta(reportId, "financial", format));
        } catch (IOException e) {
            log.error("Failed to generate financial report", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to generate report. Please try again later."));
        }
    }

    @PostMapping("/maintenance")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> generateMaintenanceReport(@RequestBody Map<String, Object> request) {
        String format = formatOf(request);
        try {
            UUID reportId = reportGeneratorService.generateMaintenanceReport(format);
            return ResponseEntity.status(HttpStatus.CREATED).body(reportMeta(reportId, "maintenance", format));
        } catch (IOException e) {
            log.error("Failed to generate maintenance report", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to generate report. Please try again later."));
        }
    }

    // ── Download endpoints ────────────────────────────────────────────────────

    @GetMapping("/assets/{reportId}/download")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<?> downloadAssetReport(@PathVariable UUID reportId) {
        return serveReport(reportId);
    }

    @GetMapping("/financial/{reportId}/download")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<?> downloadFinancialReport(@PathVariable UUID reportId) {
        return serveReport(reportId);
    }

    @GetMapping("/maintenance/{reportId}/download")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<?> downloadMaintenanceReport(@PathVariable UUID reportId) {
        return serveReport(reportId);
    }

    @GetMapping("/{reportId}/download")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<?> downloadReport(@PathVariable UUID reportId) {
        return serveReport(reportId);
    }

    // ── History / delete ──────────────────────────────────────────────────────

    @GetMapping("/history")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> getReportHistory(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        UUID orgId = TenantContext.getOrganisationId();
        if (orgId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Tenant context is required"));
        }
        if (limit <= 0) limit = 10;
        if (offset < 0) offset = 0;

        Pageable pageable = PageRequest.of(offset / limit, limit);
        Page<ReportMetadata> page = reportMetadataRepository
                .findByOrganisationIdAndDeletedAtIsNullOrderByCreatedAtDesc(orgId, pageable);

        List<ReportHistoryItemDto> reports = page.getContent().stream()
                .map(md -> {
                    ReportHistoryItemDto item = new ReportHistoryItemDto();
                    item.setReportId(md.getId().toString());
                    item.setReportType(md.getReportType());
                    item.setFormat(md.getFormat());
                    item.setFilename(md.getFilename());
                    item.setContentType(md.getContentType());
                    item.setGeneratedAt(md.getCreatedAt() == null ? null : md.getCreatedAt().toString());
                    item.setDownloadUrl("/api/v1/reports/" + md.getId() + "/download");
                    return item;
                })
                .toList();

        PagedResponseDto<ReportHistoryItemDto> response = new PagedResponseDto<>();
        response.setTotal(page.getTotalElements());
        response.setLimit(limit);
        response.setOffset(offset);
        response.setItems(reports);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{reportId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> deleteReport(@PathVariable UUID reportId) {
        UUID orgId = TenantContext.getOrganisationId();
        if (orgId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return reportMetadataRepository.findByIdAndOrganisationIdAndDeletedAtIsNull(reportId, orgId)
                .map(md -> {
                    storageService.delete(md.getStorageKey());
                    reportGeneratorService.evictFromCache(reportId);
                    reportMetadataRepository.delete(md);
                    return ResponseEntity.noContent().build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<?> serveReport(UUID reportId) {
        ReportGeneratorService.ReportEntry entry = reportGeneratorService.get(reportId);
        if (entry == null) return ResponseEntity.notFound().<byte[]>build();
        return reportGeneratorService.createDownloadUrl(reportId)
                .<ResponseEntity<?>>map(url -> ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, url)
                        .build())
                .orElseGet(() -> reportGeneratorService.download(reportId)
                        .<ResponseEntity<?>>map(obj -> ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + entry.filename() + "\"")
                                .header(HttpHeaders.CONTENT_TYPE, entry.contentType())
                                .body(obj.bytes()))
                        .orElseGet(() -> ResponseEntity.notFound().build()));
    }

    private static String formatOf(Map<String, Object> request) {
        Object fmt = request == null ? null : request.get("format");
        return fmt == null ? "PDF" : fmt.toString().toUpperCase();
    }

    private static Map<String, Object> reportMeta(UUID reportId, String type, String format) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reportId",    reportId.toString());
        m.put("format",      format);
        m.put("reportType",  type.toUpperCase());
        m.put("status",      "COMPLETED");
        m.put("downloadUrl", "/api/v1/reports/" + type + "/" + reportId + "/download");
        m.put("generatedAt", Instant.now().toString());
        return m;
    }
}
