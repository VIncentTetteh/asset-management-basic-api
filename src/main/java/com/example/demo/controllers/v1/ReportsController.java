package com.example.demo.controllers.v1;

import com.example.demo.services.EmailService;
import com.example.demo.services.impl.ReportGeneratorService;
import com.example.demo.multitenancy.TenantContext;
import com.example.demo.models.ReportMetadata;
import com.example.demo.repositories.ReportMetadataRepository;
import com.example.demo.repositories.UserRepository;
import com.example.demo.dto.PagedResponseDto;
import com.example.demo.dto.ReportHistoryItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.example.demo.storage.FileStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportsController {

    private static final Logger log = LoggerFactory.getLogger(ReportsController.class);
    private static final DateTimeFormatter REPORT_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ReportGeneratorService reportGeneratorService;
    private final ReportMetadataRepository reportMetadataRepository;
    private final FileStorageService storageService;
    private final EmailService emailService;
    private final UserRepository userRepository;

    @Value("${app.email.base-url:http://localhost:3000}")
    private String baseUrl;

    public ReportsController(ReportGeneratorService reportGeneratorService,
                              ReportMetadataRepository reportMetadataRepository,
                              FileStorageService storageService,
                              EmailService emailService,
                              UserRepository userRepository) {
        this.reportGeneratorService = reportGeneratorService;
        this.reportMetadataRepository = reportMetadataRepository;
        this.storageService = storageService;
        this.emailService = emailService;
        this.userRepository = userRepository;
    }

    // ── Generate endpoints ────────────────────────────────────────────────────

    @PostMapping("/assets")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','GENERATE_REPORTS','EXPORT_REPORTS')")
    public ResponseEntity<?> generateAssetReport(@RequestBody Map<String, Object> request) {
        String format = formatOf(request);
        try {
            UUID reportId = reportGeneratorService.generateAssetReport(format);
            Map<String, Object> meta = reportMeta(reportId, "assets", format);
            sendReportReadyEmail("Asset Register", format, reportId, "assets");
            return ResponseEntity.status(HttpStatus.CREATED).body(meta);
        } catch (IOException e) {
            log.error("Failed to generate asset report", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to generate report. Please try again later."));
        }
    }

    @PostMapping("/financial")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','GENERATE_REPORTS','EXPORT_REPORTS')")
    public ResponseEntity<?> generateFinancialReport(@RequestBody Map<String, Object> request) {
        String format = formatOf(request);
        try {
            UUID reportId = reportGeneratorService.generateFinancialReport(format);
            Map<String, Object> meta = reportMeta(reportId, "financial", format);
            sendReportReadyEmail("Financial Report", format, reportId, "financial");
            return ResponseEntity.status(HttpStatus.CREATED).body(meta);
        } catch (IOException e) {
            log.error("Failed to generate financial report", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to generate report. Please try again later."));
        }
    }

    @PostMapping("/maintenance")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','GENERATE_REPORTS','EXPORT_REPORTS')")
    public ResponseEntity<?> generateMaintenanceReport(@RequestBody Map<String, Object> request) {
        String format = formatOf(request);
        try {
            UUID reportId = reportGeneratorService.generateMaintenanceReport(format);
            Map<String, Object> meta = reportMeta(reportId, "maintenance", format);
            sendReportReadyEmail("Maintenance Report", format, reportId, "maintenance");
            return ResponseEntity.status(HttpStatus.CREATED).body(meta);
        } catch (IOException e) {
            log.error("Failed to generate maintenance report", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to generate report. Please try again later."));
        }
    }

    // ── Download endpoints ────────────────────────────────────────────────────

    @GetMapping("/assets/{reportId}/download")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_REPORTS','EXPORT_REPORTS')")
    public ResponseEntity<?> downloadAssetReport(@PathVariable UUID reportId) {
        return serveReport(reportId);
    }

    @GetMapping("/financial/{reportId}/download")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_REPORTS','EXPORT_REPORTS')")
    public ResponseEntity<?> downloadFinancialReport(@PathVariable UUID reportId) {
        return serveReport(reportId);
    }

    @GetMapping("/maintenance/{reportId}/download")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_REPORTS','EXPORT_REPORTS')")
    public ResponseEntity<?> downloadMaintenanceReport(@PathVariable UUID reportId) {
        return serveReport(reportId);
    }

    @GetMapping("/{reportId}/download")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_REPORTS','EXPORT_REPORTS')")
    public ResponseEntity<?> downloadReport(@PathVariable UUID reportId) {
        return serveReport(reportId);
    }

    // ── History / delete ──────────────────────────────────────────────────────

    @GetMapping("/history")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','GENERATE_REPORTS','EXPORT_REPORTS')")
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
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','GENERATE_REPORTS','EXPORT_REPORTS')")
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

    private void sendReportReadyEmail(String reportName, String format, UUID reportId, String type) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) return;
            String email = auth.getName();

            UUID orgId = TenantContext.getOrganisationId();
            if (orgId == null) return;

            userRepository.findByEmailAndOrganisationId(email, orgId).ifPresent(user -> {
                String now = LocalDateTime.now(ZoneOffset.UTC).format(REPORT_DATE_FMT);
                String downloadUrl = baseUrl + "/reports";
                Map<String, Object> model = new HashMap<>();
                model.put("firstName", user.getFirstName());
                model.put("reportName", reportName);
                model.put("reportType", type.substring(0, 1).toUpperCase() + type.substring(1));
                model.put("fileFormat", format);
                model.put("generatedAt", now);
                model.put("downloadUrl", downloadUrl);
                emailService.sendTemplate(
                    email,
                    "Your " + reportName + " is ready",
                    "email/report-ready",
                    model
                );
            });
        } catch (Exception e) {
            log.warn("[EMAIL] Failed to send report-ready email: {}", e.getMessage());
        }
    }
}
