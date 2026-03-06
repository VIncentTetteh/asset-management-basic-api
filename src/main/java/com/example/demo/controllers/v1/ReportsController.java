package com.example.demo.controllers.v1;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * Reports Controller
 * Handles report generation and management
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportsController {

    /**
     * POST /api/v1/reports/assets
     * Generates asset report in specified format
     */
    @PostMapping("/assets")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> generateAssetReport(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();

        String format = (String) request.getOrDefault("format", "PDF");
        UUID reportId = UUID.randomUUID();

        response.put("reportId", reportId.toString());
        response.put("format", format);
        response.put("status", "COMPLETED");
        response.put("downloadUrl", "/api/v1/reports/assets/" + reportId + "/download");
        response.put("generatedAt", Instant.now().toString());
        response.put("rowCount", 150);
        response.put("size", "2.5 MB");

        return ResponseEntity.status(201).body(response);
    }

    /**
     * POST /api/v1/reports/financial
     * Generates financial report
     */
    @PostMapping("/financial")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> generateFinancialReport(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();

        String format = (String) request.getOrDefault("format", "PDF");
        UUID reportId = UUID.randomUUID();

        response.put("reportId", reportId.toString());
        response.put("format", format);
        response.put("reportType", "FINANCIAL");
        response.put("status", "COMPLETED");
        response.put("downloadUrl", "/api/v1/reports/financial/" + reportId + "/download");
        response.put("generatedAt", Instant.now().toString());
        response.put("pages", 45);
        response.put("size", "3.2 MB");

        return ResponseEntity.status(201).body(response);
    }

    /**
     * POST /api/v1/reports/maintenance
     * Generates maintenance report
     */
    @PostMapping("/maintenance")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> generateMaintenanceReport(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();

        String format = (String) request.getOrDefault("format", "PDF");
        UUID reportId = UUID.randomUUID();

        response.put("reportId", reportId.toString());
        response.put("format", format);
        response.put("reportType", "MAINTENANCE");
        response.put("status", "COMPLETED");
        response.put("downloadUrl", "/api/v1/reports/maintenance/" + reportId + "/download");
        response.put("generatedAt", Instant.now().toString());
        response.put("maintenanceRecords", 145);
        response.put("size", "1.8 MB");

        return ResponseEntity.status(201).body(response);
    }

    /**
     * GET /api/v1/reports/{report_id}/download
     * Downloads the generated report
     */
    @GetMapping("/{reportId}/download")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<?> downloadReport(@PathVariable String reportId) {
        // In production, would retrieve actual file from storage
        byte[] fileContent = "Sample PDF Report Content".getBytes();

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"asset-report-" + reportId + ".pdf\"")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
            .body(fileContent);
    }

    /**
     * GET /api/v1/reports/history
     * Returns report generation history
     */
    @GetMapping("/history")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> getReportHistory(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        List<Map<String, Object>> reports = new ArrayList<>();

        reports.add(createReportEntry("assets", "PDF", "2026-03-05T10:30:00Z", "user@example.com", 150));
        reports.add(createReportEntry("financial", "EXCEL", "2026-03-04T14:15:00Z", "admin@example.com", 45));
        reports.add(createReportEntry("maintenance", "PDF", "2026-03-03T09:00:00Z", "user@example.com", 145));
        reports.add(createReportEntry("assets", "CSV", "2026-03-02T16:30:00Z", "admin@example.com", 150));

        Map<String, Object> response = new HashMap<>();
        response.put("totalReports", 4);
        response.put("limit", limit);
        response.put("offset", offset);
        response.put("reports", reports);

        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/v1/reports/{report_id}
     * Deletes report from storage
     */
    @DeleteMapping("/{reportId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> deleteReport(@PathVariable String reportId) {
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> createReportEntry(String type, String format, String generatedAt, String generatedBy, int rowCount) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("reportId", UUID.randomUUID().toString());
        entry.put("type", type);
        entry.put("format", format);
        entry.put("generatedAt", generatedAt);
        entry.put("generatedBy", generatedBy);
        entry.put("rowCount", rowCount);
        entry.put("downloadUrl", "/api/v1/reports/{reportId}/download");
        return entry;
    }
}


