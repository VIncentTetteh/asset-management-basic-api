package com.example.demo.controllers.v1;

import com.example.demo.services.impl.ReportGeneratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportsController {

    private static final Logger log = LoggerFactory.getLogger(ReportsController.class);

    private final ReportGeneratorService reportGeneratorService;

    public ReportsController(ReportGeneratorService reportGeneratorService) {
        this.reportGeneratorService = reportGeneratorService;
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
        Map<String, Object> response = new HashMap<>();
        response.put("totalReports", 0);
        response.put("limit", limit);
        response.put("offset", offset);
        response.put("reports", List.of());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{reportId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> deleteReport(@PathVariable UUID reportId) {
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<?> serveReport(UUID reportId) {
        ReportGeneratorService.ReportEntry entry = reportGeneratorService.get(reportId);
        if (entry == null) return ResponseEntity.notFound().<byte[]>build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + entry.filename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, entry.contentType())
                .body(entry.bytes());
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
