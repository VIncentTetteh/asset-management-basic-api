package com.assetiq.services;

import com.assetiq.models.Organisation;
import com.assetiq.models.compliance.BogControl;
import com.assetiq.models.compliance.ControlStatus;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.compliance.BogControlRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BogComplianceReportService")
class BogComplianceReportServiceTest {

    @Mock OrganisationRepository organisationRepository;
    @Mock BogControlRepository   bogControlRepository;

    BogComplianceReportService service;
    Organisation org;

    @BeforeEach
    void setUp() {
        service = new BogComplianceReportService(organisationRepository, bogControlRepository);

        org = new Organisation();
        org.setId(UUID.randomUUID());
        org.setName("Test Bank");

        TenantContext.setOrganisationId(org.getId());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@bank.com", null, List.of()));
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId()))
                .thenReturn(Optional.of(org));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ── Compliance percentage calculation ─────────────────────────────────────

    @Nested
    @DisplayName("compliancePercent calculation")
    class CompliancePercent {

        @Test
        @DisplayName("100% when all applicable controls are IMPLEMENTED")
        void allImplemented_100pct() {
            stubControls(List.of(
                    control("ICT.1.1", ControlStatus.IMPLEMENTED),
                    control("ICT.1.2", ControlStatus.IMPLEMENTED)));

            Map<String, Object> report = service.generateReport();
            Map<?, ?> summary = (Map<?, ?>) report.get("summary");

            assertThat(summary.get("compliancePercent")).isEqualTo(100.0);
            assertThat(summary.get("overallStatus")).isEqualTo("COMPLIANT");
        }

        @Test
        @DisplayName("0% when all applicable controls are NOT_IMPLEMENTED")
        void allNotImplemented_0pct() {
            stubControls(List.of(
                    control("ICT.2.1", ControlStatus.NOT_IMPLEMENTED),
                    control("ICT.2.2", ControlStatus.NOT_IMPLEMENTED)));

            Map<String, Object> report = service.generateReport();
            Map<?, ?> summary = (Map<?, ?>) report.get("summary");

            assertThat(summary.get("compliancePercent")).isEqualTo(0.0);
            assertThat(summary.get("overallStatus")).isEqualTo("NON_COMPLIANT");
        }

        @Test
        @DisplayName("PARTIAL counts as 0.5 toward compliance score")
        void partialCountsAsHalf() {
            // 2 implemented (2.0) + 2 partial (1.0) out of 4 applicable = 75%
            stubControls(List.of(
                    control("ICT.3.1", ControlStatus.IMPLEMENTED),
                    control("ICT.3.2", ControlStatus.IMPLEMENTED),
                    control("ICT.3.3", ControlStatus.PARTIAL),
                    control("ICT.3.4", ControlStatus.PARTIAL)));

            Map<?, ?> summary = (Map<?, ?>) service.generateReport().get("summary");

            assertThat(summary.get("compliancePercent")).isEqualTo(75.0);
            assertThat(summary.get("overallStatus")).isEqualTo("PARTIALLY_COMPLIANT");
        }

        @Test
        @DisplayName("NOT_APPLICABLE controls are excluded from denominator")
        void notApplicableExcludedFromDenominator() {
            // 1 implemented out of 2 applicable (1 N/A excluded) = 50%
            stubControls(List.of(
                    control("ORM.1.1", ControlStatus.IMPLEMENTED),
                    control("ORM.1.2", ControlStatus.NOT_IMPLEMENTED),
                    control("ORM.1.3", ControlStatus.NOT_APPLICABLE)));

            Map<?, ?> summary = (Map<?, ?>) service.generateReport().get("summary");

            assertThat(summary.get("compliancePercent")).isEqualTo(50.0);
            assertThat((Long) summary.get("notApplicable")).isEqualTo(1L);
        }

        @Test
        @DisplayName("0% when all controls are NOT_APPLICABLE (no applicable denominator)")
        void allNotApplicable_zeroPercent() {
            stubControls(List.of(
                    control("ICT.9.1", ControlStatus.NOT_APPLICABLE)));

            Map<?, ?> summary = (Map<?, ?>) service.generateReport().get("summary");

            assertThat(summary.get("compliancePercent")).isEqualTo(0.0);
        }
    }

    // ── RAG status thresholds ─────────────────────────────────────────────────

    @Nested
    @DisplayName("RAG status thresholds")
    class RagStatus {

        @Test
        @DisplayName("≥80% → COMPLIANT")
        void above80_compliant() {
            // 8 of 10 implemented = 80%
            stubControls(nControls("ICT", ControlStatus.IMPLEMENTED, 8,
                    ControlStatus.NOT_IMPLEMENTED, 2));

            Map<?, ?> summary = (Map<?, ?>) service.generateReport().get("summary");
            assertThat(summary.get("overallStatus")).isEqualTo("COMPLIANT");
        }

        @Test
        @DisplayName("≥50% but <80% → PARTIALLY_COMPLIANT")
        void between50and80_partiallyCompliant() {
            // 6 of 10 implemented = 60%
            stubControls(nControls("ORM", ControlStatus.IMPLEMENTED, 6,
                    ControlStatus.NOT_IMPLEMENTED, 4));

            Map<?, ?> summary = (Map<?, ?>) service.generateReport().get("summary");
            assertThat(summary.get("overallStatus")).isEqualTo("PARTIALLY_COMPLIANT");
        }

        @Test
        @DisplayName("<50% → NON_COMPLIANT")
        void below50_nonCompliant() {
            // 3 of 10 implemented = 30%
            stubControls(nControls("DRP", ControlStatus.IMPLEMENTED, 3,
                    ControlStatus.NOT_IMPLEMENTED, 7));

            Map<?, ?> summary = (Map<?, ?>) service.generateReport().get("summary");
            assertThat(summary.get("overallStatus")).isEqualTo("NON_COMPLIANT");
        }
    }

    // ── Domain grouping ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("domain grouping")
    class DomainGrouping {

        @Test
        @DisplayName("groups controls by directive prefix and includes per-domain percentages")
        void groupsByPrefix() {
            stubControls(List.of(
                    control("ICT.1.1", ControlStatus.IMPLEMENTED),
                    control("ICT.1.2", ControlStatus.NOT_IMPLEMENTED),
                    control("ORM.2.1", ControlStatus.IMPLEMENTED),
                    control("ORM.2.2", ControlStatus.IMPLEMENTED)));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> domains =
                    (List<Map<String, Object>>) service.generateReport().get("domains");

            assertThat(domains).hasSize(2);
            Map<String, Object> ict = findDomain(domains, "ICT");
            Map<String, Object> orm = findDomain(domains, "ORM");

            assertThat(ict.get("compliancePercent")).isEqualTo(50.0);
            assertThat(orm.get("compliancePercent")).isEqualTo(100.0);
        }

        @Test
        @DisplayName("domains are sorted alphabetically by prefix")
        void domainsAreSortedAlphabetically() {
            stubControls(List.of(
                    control("ORM.1.1", ControlStatus.IMPLEMENTED),
                    control("DRP.1.1", ControlStatus.IMPLEMENTED),
                    control("ICT.1.1", ControlStatus.IMPLEMENTED)));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> domains =
                    (List<Map<String, Object>>) service.generateReport().get("domains");

            assertThat(domains).extracting(d -> d.get("domain"))
                    .containsExactly("DRP", "ICT", "ORM");
        }

        @Test
        @DisplayName("directive ref with no dot is used as-is for domain")
        void noDotInRef_usedAsIs() {
            stubControls(List.of(control("GENERAL", ControlStatus.IMPLEMENTED)));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> domains =
                    (List<Map<String, Object>>) service.generateReport().get("domains");

            assertThat(domains).extracting(d -> d.get("domain")).contains("GENERAL");
        }
    }

    // ── Open gaps list ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("open gaps")
    class OpenGaps {

        @Test
        @DisplayName("includes NOT_IMPLEMENTED and PARTIAL controls in gaps list")
        void includesNotImplementedAndPartial() {
            stubControls(List.of(
                    control("ICT.1.1", ControlStatus.IMPLEMENTED),
                    control("ICT.1.2", ControlStatus.NOT_IMPLEMENTED),
                    control("ICT.1.3", ControlStatus.PARTIAL)));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> gaps =
                    (List<Map<String, Object>>) service.generateReport().get("openGaps");

            assertThat(gaps).hasSize(2);
            assertThat(gaps).extracting(g -> g.get("directiveRef"))
                    .containsExactlyInAnyOrder("ICT.1.2", "ICT.1.3");
        }

        @Test
        @DisplayName("excludes IMPLEMENTED and NOT_APPLICABLE from gaps list")
        void excludesImplementedAndNotApplicable() {
            stubControls(List.of(
                    control("ICT.2.1", ControlStatus.IMPLEMENTED),
                    control("ICT.2.2", ControlStatus.NOT_APPLICABLE)));

            @SuppressWarnings("unchecked")
            List<?> gaps = (List<?>) service.generateReport().get("openGaps");

            assertThat(gaps).isEmpty();
        }

        @Test
        @DisplayName("gaps are sorted by directive reference")
        void gapsSortedByDirectiveRef() {
            stubControls(List.of(
                    control("ORM.3.1", ControlStatus.NOT_IMPLEMENTED),
                    control("ICT.1.1", ControlStatus.NOT_IMPLEMENTED),
                    control("DRP.2.1", ControlStatus.NOT_IMPLEMENTED)));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> gaps =
                    (List<Map<String, Object>>) service.generateReport().get("openGaps");

            assertThat(gaps).extracting(g -> g.get("directiveRef"))
                    .containsExactly("DRP.2.1", "ICT.1.1", "ORM.3.1");
        }
    }

    // ── Report structure ──────────────────────────────────────────────────────

    @Test
    @DisplayName("report includes required top-level keys")
    void reportStructure_hasRequiredKeys() {
        stubControls(List.of());

        Map<String, Object> report = service.generateReport();

        assertThat(report).containsKeys(
                "reportType", "organisationId", "organisationName",
                "generatedAt", "summary", "domains", "openGaps");
        assertThat(report.get("reportType")).isEqualTo("BOG_ICT_DIRECTIVE");
        assertThat(report.get("organisationId")).isEqualTo(org.getId());
    }

    @Test
    @DisplayName("summary counts match the list of controls")
    void summaryCounts_matchControls() {
        stubControls(List.of(
                control("ICT.1.1", ControlStatus.IMPLEMENTED),
                control("ICT.1.2", ControlStatus.PARTIAL),
                control("ICT.1.3", ControlStatus.NOT_IMPLEMENTED),
                control("ICT.1.4", ControlStatus.NOT_APPLICABLE)));

        Map<?, ?> summary = (Map<?, ?>) service.generateReport().get("summary");

        assertThat(summary.get("totalControls")).isEqualTo(4L);
        assertThat(summary.get("implemented")).isEqualTo(1L);
        assertThat(summary.get("partial")).isEqualTo(1L);
        assertThat(summary.get("notImplemented")).isEqualTo(1L);
        assertThat(summary.get("notApplicable")).isEqualTo(1L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void stubControls(List<BogControl> controls) {
        when(bogControlRepository.findByOrganisationAndDeletedAtIsNull(org))
                .thenReturn(controls);
    }

    private BogControl control(String ref, ControlStatus status) {
        BogControl c = new BogControl();
        c.setOrganisation(org);
        c.setDirectiveRef(ref);
        c.setRequirement("Requirement for " + ref);
        c.setStatus(status);
        return c;
    }

    /** Creates {@code aCount} controls with {@code aStatus} + {@code bCount} with {@code bStatus}. */
    private List<BogControl> nControls(String domain,
                                       ControlStatus aStatus, int aCount,
                                       ControlStatus bStatus, int bCount) {
        List<BogControl> list = new java.util.ArrayList<>();
        for (int i = 0; i < aCount; i++) list.add(control(domain + ".1." + i, aStatus));
        for (int i = 0; i < bCount; i++) list.add(control(domain + ".2." + i, bStatus));
        return list;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findDomain(List<Map<String, Object>> domains, String name) {
        return domains.stream()
                .filter(d -> name.equals(d.get("domain")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Domain not found: " + name));
    }
}
