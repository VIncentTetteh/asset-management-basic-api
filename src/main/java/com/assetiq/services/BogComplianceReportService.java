package com.assetiq.services;

import com.assetiq.models.Organisation;
import com.assetiq.models.compliance.BogControl;
import com.assetiq.models.compliance.ControlStatus;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.compliance.BogControlRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates Bank of Ghana (BOG) ICT Security Directive compliance reports
 * for the current tenant organisation.
 *
 * <p>The BOG ICT Directive (2018) is grouped into functional domains identified
 * by the directive reference prefix (e.g. "ICT", "ORM", "DRP", "ISMS", "CS").
 * This service aggregates control statuses, calculates per-domain completion
 * percentages, and surfaces open gaps with remediation details.
 *
 * <p>The report JSON is structured for direct consumption by the front-end
 * dashboard and for export to PDF by downstream report generators.
 */
@Service
public class BogComplianceReportService extends TenantAwareService {

    private final BogControlRepository bogControlRepository;

    public BogComplianceReportService(OrganisationRepository organisationRepository,
                                      BogControlRepository bogControlRepository) {
        super(organisationRepository);
        this.bogControlRepository = bogControlRepository;
    }

    /**
     * Build a full compliance report for the currently authenticated tenant.
     *
     * @return structured report map ready for JSON serialisation
     */
    @Transactional(readOnly = true)
    public Map<String, Object> generateReport() {
        Organisation org = requireTenantOrg();
        List<BogControl> controls = bogControlRepository.findByOrganisationAndDeletedAtIsNull(org);

        // ── Aggregate overall stats ───────────────────────────────────────────
        long total          = controls.size();
        long implemented    = count(controls, ControlStatus.IMPLEMENTED);
        long partial        = count(controls, ControlStatus.PARTIAL);
        long notImplemented = count(controls, ControlStatus.NOT_IMPLEMENTED);
        long notApplicable  = count(controls, ControlStatus.NOT_APPLICABLE);

        // Compliance % ignores NOT_APPLICABLE controls
        long applicable = total - notApplicable;
        double compliancePct = applicable == 0 ? 0.0
                : Math.round(((implemented + partial * 0.5) / applicable) * 1000.0) / 10.0;

        // ── Per-domain breakdown ──────────────────────────────────────────────
        Map<String, List<BogControl>> byDomain = controls.stream()
                .collect(Collectors.groupingBy(c -> extractDomain(c.getDirectiveRef())));

        List<Map<String, Object>> domains = byDomain.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> buildDomainSection(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        // ── Open gaps ─────────────────────────────────────────────────────────
        List<Map<String, Object>> openGaps = controls.stream()
                .filter(c -> c.getStatus() == ControlStatus.NOT_IMPLEMENTED
                        || c.getStatus() == ControlStatus.PARTIAL)
                .sorted(Comparator.comparing(BogControl::getDirectiveRef))
                .map(this::buildGapEntry)
                .collect(Collectors.toList());

        // ── Assemble report ───────────────────────────────────────────────────
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalControls",      total);
        summary.put("implemented",         implemented);
        summary.put("partial",             partial);
        summary.put("notImplemented",      notImplemented);
        summary.put("notApplicable",       notApplicable);
        summary.put("compliancePercent",   compliancePct);
        summary.put("overallStatus",       deriveStatus(compliancePct));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportType",      "BOG_ICT_DIRECTIVE");
        report.put("organisationId",  org.getId());
        report.put("organisationName",org.getName());
        report.put("generatedAt",     Instant.now().toString());
        report.put("summary",         summary);
        report.put("domains",         domains);
        report.put("openGaps",        openGaps);

        return report;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private long count(List<BogControl> controls, ControlStatus status) {
        return controls.stream().filter(c -> c.getStatus() == status).count();
    }

    /** Extracts the domain prefix from a directive reference like "ICT.4.1" → "ICT". */
    private String extractDomain(String directiveRef) {
        if (directiveRef == null) return "OTHER";
        int dot = directiveRef.indexOf('.');
        return dot > 0 ? directiveRef.substring(0, dot) : directiveRef;
    }

    private Map<String, Object> buildDomainSection(String domain, List<BogControl> controls) {
        long total          = controls.size();
        long implemented    = count(controls, ControlStatus.IMPLEMENTED);
        long partial        = count(controls, ControlStatus.PARTIAL);
        long notImplemented = count(controls, ControlStatus.NOT_IMPLEMENTED);
        long notApplicable  = count(controls, ControlStatus.NOT_APPLICABLE);
        long applicable     = total - notApplicable;
        double pct = applicable == 0 ? 0.0
                : Math.round(((implemented + partial * 0.5) / applicable) * 1000.0) / 10.0;

        Map<String, Object> section = new LinkedHashMap<>();
        section.put("domain",          domain);
        section.put("totalControls",   total);
        section.put("implemented",     implemented);
        section.put("partial",         partial);
        section.put("notImplemented",  notImplemented);
        section.put("notApplicable",   notApplicable);
        section.put("compliancePercent", pct);
        section.put("status",          deriveStatus(pct));
        return section;
    }

    private Map<String, Object> buildGapEntry(BogControl c) {
        Map<String, Object> gap = new LinkedHashMap<>();
        gap.put("directiveRef",      c.getDirectiveRef());
        gap.put("requirement",       c.getRequirement());
        gap.put("status",            c.getStatus().name());
        gap.put("gapDescription",    c.getGapDescription());
        gap.put("remediationPlan",   c.getRemediationPlan());
        gap.put("targetDate",        c.getTargetDate() != null ? c.getTargetDate().toString() : null);
        gap.put("evidenceUrl",       c.getEvidenceUrl());
        if (c.getOwner() != null) {
            gap.put("owner", c.getOwner().getFirstName() + " " + c.getOwner().getLastName());
        }
        return gap;
    }

    /** Translate compliance % to a RAG status label. */
    private String deriveStatus(double pct) {
        if (pct >= 80) return "COMPLIANT";
        if (pct >= 50) return "PARTIALLY_COMPLIANT";
        return "NON_COMPLIANT";
    }
}
