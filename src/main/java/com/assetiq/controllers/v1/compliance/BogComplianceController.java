package com.assetiq.controllers.v1.compliance;

import com.assetiq.models.Organisation;
import com.assetiq.models.compliance.BogControl;
import com.assetiq.models.compliance.ControlStatus;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.compliance.BogControlRepository;
import com.assetiq.services.BogComplianceReportService;
import com.assetiq.services.TenantAwareService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BOG ICT Directive compliance report endpoints.
 *
 * <p>Base path: {@code /api/v1/compliance/bog}
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /report}         — Full compliance report (JSON)</li>
 *   <li>{@code GET /report/pdf}     — Same report exported as PDF</li>
 *   <li>{@code POST /controls}      — Upsert a BOG control entry</li>
 *   <li>{@code PATCH /controls/{id}/status} — Update control status</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/compliance/bog")
@PreAuthorize("isAuthenticated()")
public class BogComplianceController extends TenantAwareService {

    private final BogComplianceReportService reportService;
    private final BogControlRepository bogControlRepository;

    public BogComplianceController(OrganisationRepository organisationRepository,
                                   BogComplianceReportService reportService,
                                   BogControlRepository bogControlRepository) {
        super(organisationRepository);
        this.reportService = reportService;
        this.bogControlRepository = bogControlRepository;
    }

    // ── GET /report — JSON ───────────────────────────────────────────────────

    /**
     * Returns the full BOG ICT Directive compliance report as JSON.
     * Includes summary statistics, per-domain breakdown, and open gap details.
     */
    @GetMapping("/report")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_REPORTS','MANAGE_COMPLIANCE')")
    public ResponseEntity<Map<String, Object>> getReport() {
        return ResponseEntity.ok(reportService.generateReport());
    }

    // ── GET /report/pdf — PDF ────────────────────────────────────────────────

    /**
     * Returns the BOG ICT Directive compliance report as a downloadable PDF.
     * Uses Apache PDFBox for rendering — no template dependency.
     */
    @GetMapping("/report/pdf")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_REPORTS','MANAGE_COMPLIANCE')")
    public ResponseEntity<byte[]> getReportPdf() {
        Map<String, Object> report = reportService.generateReport();
        byte[] pdf = buildPdf(report);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"bog-compliance-report.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }

    // ── POST /controls — upsert ──────────────────────────────────────────────

    /**
     * Create or update a BOG control entry.
     * If a control with the same {@code directiveRef} already exists for this org,
     * it is updated; otherwise a new record is created.
     */
    @PostMapping("/controls")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_COMPLIANCE')")
    public ResponseEntity<Map<String, Object>> upsertControl(@RequestBody Map<String, Object> body) {
        Organisation org = requireTenantOrg();
        String ref = (String) body.get("directiveRef");
        if (ref == null || ref.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "directiveRef is required"));
        }

        BogControl control = bogControlRepository
                .findByOrganisationAndDirectiveRefAndDeletedAtIsNull(org, ref)
                .orElseGet(BogControl::new);

        control.setOrganisation(org);
        control.setDirectiveRef(ref);
        if (body.containsKey("requirement"))     control.setRequirement((String) body.get("requirement"));
        if (body.containsKey("status")) {
            try {
                control.setStatus(ControlStatus.valueOf((String) body.get("status")));
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid status value"));
            }
        }
        if (body.containsKey("gapDescription"))   control.setGapDescription((String) body.get("gapDescription"));
        if (body.containsKey("remediationPlan"))   control.setRemediationPlan((String) body.get("remediationPlan"));
        if (body.containsKey("evidenceUrl"))       control.setEvidenceUrl((String) body.get("evidenceUrl"));
        if (body.containsKey("targetDate")) {
            try {
                control.setTargetDate(Instant.parse((String) body.get("targetDate")));
            } catch (Exception ignored) { /* best-effort */ }
        }

        BogControl saved = bogControlRepository.save(control);
        return ResponseEntity.ok(Map.of(
                "id", saved.getId(),
                "directiveRef", saved.getDirectiveRef(),
                "status", saved.getStatus().name()));
    }

    // ── PATCH /controls/{id}/status ─────────────────────────────────────────

    /**
     * Update the implementation status of a single BOG control.
     * Body: {@code { "status": "IMPLEMENTED" }}
     */
    @PatchMapping("/controls/{id}/status")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_COMPLIANCE')")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        Organisation org = requireTenantOrg();
        BogControl control = bogControlRepository
                .findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Control not found: " + id));

        String statusStr = body.get("status");
        if (statusStr == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
        }
        try {
            control.setStatus(ControlStatus.valueOf(statusStr));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid status: " + statusStr));
        }
        bogControlRepository.save(control);
        return ResponseEntity.ok(Map.of("id", id, "status", statusStr));
    }

    // ── PDF builder ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private byte[] buildPdf(Map<String, Object> report) {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float margin = 50;
                float yStart = PDRectangle.A4.getHeight() - margin;
                float y = yStart;
                float lineHeight = 14;

                PDType1Font bold   = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                // Title
                cs.beginText();
                cs.setFont(bold, 16);
                cs.newLineAtOffset(margin, y);
                cs.showText("Bank of Ghana ICT Directive — Compliance Report");
                cs.endText();
                y -= lineHeight * 2;

                // Org + date
                cs.beginText();
                cs.setFont(normal, 10);
                cs.newLineAtOffset(margin, y);
                cs.showText("Organisation: " + report.getOrDefault("organisationName", "—"));
                cs.endText();
                y -= lineHeight;
                cs.beginText();
                cs.setFont(normal, 10);
                cs.newLineAtOffset(margin, y);
                cs.showText("Generated: " + report.getOrDefault("generatedAt", "—"));
                cs.endText();
                y -= lineHeight * 2;

                // Summary
                Map<String, Object> summary = (Map<String, Object>) report.get("summary");
                if (summary != null) {
                    cs.beginText();
                    cs.setFont(bold, 12);
                    cs.newLineAtOffset(margin, y);
                    cs.showText("Summary");
                    cs.endText();
                    y -= lineHeight;

                    for (Map.Entry<String, Object> e : summary.entrySet()) {
                        cs.beginText();
                        cs.setFont(normal, 10);
                        cs.newLineAtOffset(margin + 10, y);
                        cs.showText(e.getKey() + ": " + e.getValue());
                        cs.endText();
                        y -= lineHeight;
                        if (y < margin + 60) break; // simple overflow guard
                    }
                }
                y -= lineHeight;

                // Domain table header
                cs.beginText();
                cs.setFont(bold, 12);
                cs.newLineAtOffset(margin, y);
                cs.showText("Domain Breakdown");
                cs.endText();
                y -= lineHeight;

                var domains = (List<Map<String, Object>>) report.get("domains");
                if (domains != null) {
                    for (Map<String, Object> domain : domains) {
                        if (y < margin + 40) break;
                        String line = String.format("%-10s  Implemented: %s  Partial: %s  Gap: %s  Score: %s%%",
                                domain.get("domain"),
                                domain.get("implemented"),
                                domain.get("partial"),
                                domain.get("notImplemented"),
                                domain.get("compliancePercent"));
                        cs.beginText();
                        cs.setFont(normal, 9);
                        cs.newLineAtOffset(margin + 10, y);
                        cs.showText(line.length() > 100 ? line.substring(0, 100) : line);
                        cs.endText();
                        y -= lineHeight;
                    }
                }
            }

            doc.save(out);
            return out.toByteArray();

        } catch (Exception ex) {
            throw new IllegalStateException("PDF generation failed: " + ex.getMessage(), ex);
        }
    }
}
