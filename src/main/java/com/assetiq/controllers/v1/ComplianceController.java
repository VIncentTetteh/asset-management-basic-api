package com.assetiq.controllers.v1;

import com.assetiq.dto.compliance.*;
import com.assetiq.dto.PagedResponseDto;
import com.assetiq.services.ComplianceService;
import com.assetiq.validation.OnCreate;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_AUDIT_LOGS','CONDUCT_AUDIT','MANAGE_SECURITY_SETTINGS','VIEW_COMPLIANCE','MANAGE_COMPLIANCE')")
public class ComplianceController {

    /**
     * Every compliance write. The class-level rule is read access and includes
     * VIEW_COMPLIANCE and VIEW_AUDIT_LOGS, which let read-only users create,
     * edit and delete controls, risks, incidents and filings.
     */
    static final String WRITE =
            "hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_COMPLIANCE','MANAGE_SECURITY_SETTINGS')";

    private final ComplianceService complianceService;

    public ComplianceController(ComplianceService complianceService) {
        this.complianceService = complianceService;
    }

    // ── ComplianceControl ────────────────────────────────────────────────────

    @GetMapping("/controls")
    public ResponseEntity<List<ComplianceControlDto>> listControls(
            @RequestParam(required = false) String framework,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(complianceService.listControls(framework, status));
    }

    @GetMapping("/controls/{id}")
    public ResponseEntity<ComplianceControlDto> getControl(@PathVariable UUID id) {
        return ResponseEntity.ok(complianceService.getControl(id));
    }

    @PostMapping("/controls")
    @PreAuthorize(WRITE)
    public ResponseEntity<ComplianceControlDto> createControl(@Validated(OnCreate.class) @RequestBody ComplianceControlDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createControl(dto));
    }

    @PatchMapping("/controls/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<ComplianceControlDto> updateControl(
            @PathVariable UUID id, @Valid @RequestBody ComplianceControlDto dto) {
        return ResponseEntity.ok(complianceService.updateControl(id, dto));
    }

    /** Full replace: every optional field absent from the body is cleared. */
    @PutMapping("/controls/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<ComplianceControlDto> replaceControl(
            @PathVariable UUID id, @Validated(OnCreate.class) @RequestBody ComplianceControlDto dto) {
        return ResponseEntity.ok(complianceService.replaceControl(id, dto));
    }

    @DeleteMapping("/controls/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<Void> deleteControl(@PathVariable UUID id) {
        complianceService.deleteControl(id);
        return ResponseEntity.noContent().build();
    }

    // ── BogControl ───────────────────────────────────────────────────────────

    @GetMapping("/bog-controls")
    public ResponseEntity<List<BogControlDto>> listBogControls(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(complianceService.listBogControls(status));
    }

    @GetMapping("/bog-controls/{id}")
    public ResponseEntity<BogControlDto> getBogControl(@PathVariable UUID id) {
        return ResponseEntity.ok(complianceService.getBogControl(id));
    }

    @PostMapping("/bog-controls")
    @PreAuthorize(WRITE)
    public ResponseEntity<BogControlDto> createBogControl(@Validated(OnCreate.class) @RequestBody BogControlDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createBogControl(dto));
    }

    @PatchMapping("/bog-controls/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<BogControlDto> updateBogControl(
            @PathVariable UUID id, @Valid @RequestBody BogControlDto dto) {
        return ResponseEntity.ok(complianceService.updateBogControl(id, dto));
    }

    /** Full replace: every optional field absent from the body is cleared. */
    @PutMapping("/bog-controls/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<BogControlDto> replaceBogControl(
            @PathVariable UUID id, @Validated(OnCreate.class) @RequestBody BogControlDto dto) {
        return ResponseEntity.ok(complianceService.replaceBogControl(id, dto));
    }

    @DeleteMapping("/bog-controls/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<Void> deleteBogControl(@PathVariable UUID id) {
        complianceService.deleteBogControl(id);
        return ResponseEntity.noContent().build();
    }

    // ── RiskRegister ─────────────────────────────────────────────────────────

    @GetMapping("/risks")
    public ResponseEntity<PagedResponseDto<RiskRegisterDto>> listRisks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long offset,
            @PageableDefault(size = 20) Pageable pageable) {
        int effectiveLimit = (limit != null && limit > 0) ? limit : pageable.getPageSize();
        long effectiveOffset = (offset != null && offset >= 0)
                ? offset
                : (long) pageable.getPageNumber() * effectiveLimit;

        Pageable effectivePageable = PageRequest.of((int) (effectiveOffset / effectiveLimit), effectiveLimit, pageable.getSort());
        Page<RiskRegisterDto> page = complianceService.listRisks(status, effectivePageable);

        PagedResponseDto<RiskRegisterDto> response = new PagedResponseDto<>();
        response.setTotal(page.getTotalElements());
        response.setLimit(effectiveLimit);
        response.setOffset(effectiveOffset);
        response.setItems(page.getContent());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/risks/{id}")
    public ResponseEntity<RiskRegisterDto> getRisk(@PathVariable UUID id) {
        return ResponseEntity.ok(complianceService.getRisk(id));
    }

    @PostMapping("/risks")
    @PreAuthorize(WRITE)
    public ResponseEntity<RiskRegisterDto> createRisk(@Validated(OnCreate.class) @RequestBody RiskRegisterDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createRisk(dto));
    }

    @PatchMapping("/risks/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<RiskRegisterDto> updateRisk(
            @PathVariable UUID id, @Valid @RequestBody RiskRegisterDto dto) {
        return ResponseEntity.ok(complianceService.updateRisk(id, dto));
    }

    /** Full replace: every optional field absent from the body is cleared. */
    @PutMapping("/risks/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<RiskRegisterDto> replaceRisk(
            @PathVariable UUID id, @Validated(OnCreate.class) @RequestBody RiskRegisterDto dto) {
        return ResponseEntity.ok(complianceService.replaceRisk(id, dto));
    }

    @DeleteMapping("/risks/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<Void> deleteRisk(@PathVariable UUID id) {
        complianceService.deleteRisk(id);
        return ResponseEntity.noContent().build();
    }

    // ── SecurityIncident ─────────────────────────────────────────────────────

    @GetMapping("/incidents")
    public ResponseEntity<PagedResponseDto<SecurityIncidentDto>> listIncidents(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long offset,
            @PageableDefault(size = 20) Pageable pageable) {
        int effectiveLimit = (limit != null && limit > 0) ? limit : pageable.getPageSize();
        long effectiveOffset = (offset != null && offset >= 0)
                ? offset
                : (long) pageable.getPageNumber() * effectiveLimit;

        Pageable effectivePageable = PageRequest.of((int) (effectiveOffset / effectiveLimit), effectiveLimit, pageable.getSort());
        Page<SecurityIncidentDto> page = complianceService.listIncidents(effectivePageable);

        PagedResponseDto<SecurityIncidentDto> response = new PagedResponseDto<>();
        response.setTotal(page.getTotalElements());
        response.setLimit(effectiveLimit);
        response.setOffset(effectiveOffset);
        response.setItems(page.getContent());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/incidents/{id}")
    public ResponseEntity<SecurityIncidentDto> getIncident(@PathVariable UUID id) {
        return ResponseEntity.ok(complianceService.getIncident(id));
    }

    @PostMapping("/incidents")
    @PreAuthorize(WRITE)
    public ResponseEntity<SecurityIncidentDto> createIncident(@Validated(OnCreate.class) @RequestBody SecurityIncidentDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createIncident(dto));
    }

    @PatchMapping("/incidents/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<SecurityIncidentDto> updateIncident(
            @PathVariable UUID id, @Valid @RequestBody SecurityIncidentDto dto) {
        return ResponseEntity.ok(complianceService.updateIncident(id, dto));
    }

    /** Full replace: every optional field absent from the body is cleared. */
    @PutMapping("/incidents/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<SecurityIncidentDto> replaceIncident(
            @PathVariable UUID id, @Validated(OnCreate.class) @RequestBody SecurityIncidentDto dto) {
        return ResponseEntity.ok(complianceService.replaceIncident(id, dto));
    }

    @DeleteMapping("/incidents/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<Void> deleteIncident(@PathVariable UUID id) {
        complianceService.deleteIncident(id);
        return ResponseEntity.noContent().build();
    }

    // ── SecurityPolicy ───────────────────────────────────────────────────────

    @GetMapping("/policies")
    public ResponseEntity<List<SecurityPolicyDto>> listPolicies() {
        return ResponseEntity.ok(complianceService.listPolicies());
    }

    @GetMapping("/policies/{id}")
    public ResponseEntity<SecurityPolicyDto> getPolicy(@PathVariable UUID id) {
        return ResponseEntity.ok(complianceService.getPolicy(id));
    }

    @PostMapping("/policies")
    @PreAuthorize(WRITE)
    public ResponseEntity<SecurityPolicyDto> createPolicy(@Validated(OnCreate.class) @RequestBody SecurityPolicyDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createPolicy(dto));
    }

    @PatchMapping("/policies/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<SecurityPolicyDto> updatePolicy(
            @PathVariable UUID id, @Valid @RequestBody SecurityPolicyDto dto) {
        return ResponseEntity.ok(complianceService.updatePolicy(id, dto));
    }

    /** Full replace: every optional field absent from the body is cleared. */
    @PutMapping("/policies/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<SecurityPolicyDto> replacePolicy(
            @PathVariable UUID id, @Validated(OnCreate.class) @RequestBody SecurityPolicyDto dto) {
        return ResponseEntity.ok(complianceService.replacePolicy(id, dto));
    }

    @DeleteMapping("/policies/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<Void> deletePolicy(@PathVariable UUID id) {
        complianceService.deletePolicy(id);
        return ResponseEntity.noContent().build();
    }

    // ── SecurityZone ─────────────────────────────────────────────────────────

    @GetMapping("/security-zones")
    public ResponseEntity<List<SecurityZoneDto>> listSecurityZones() {
        return ResponseEntity.ok(complianceService.listSecurityZones());
    }

    @GetMapping("/security-zones/{id}")
    public ResponseEntity<SecurityZoneDto> getSecurityZone(@PathVariable UUID id) {
        return ResponseEntity.ok(complianceService.getSecurityZone(id));
    }

    @PostMapping("/security-zones")
    @PreAuthorize(WRITE)
    public ResponseEntity<SecurityZoneDto> createSecurityZone(@Validated(OnCreate.class) @RequestBody SecurityZoneDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createSecurityZone(dto));
    }

    @PatchMapping("/security-zones/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<SecurityZoneDto> updateSecurityZone(
            @PathVariable UUID id, @Valid @RequestBody SecurityZoneDto dto) {
        return ResponseEntity.ok(complianceService.updateSecurityZone(id, dto));
    }

    /** Full replace: every optional field absent from the body is cleared. */
    @PutMapping("/security-zones/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<SecurityZoneDto> replaceSecurityZone(
            @PathVariable UUID id, @Validated(OnCreate.class) @RequestBody SecurityZoneDto dto) {
        return ResponseEntity.ok(complianceService.replaceSecurityZone(id, dto));
    }

    @DeleteMapping("/security-zones/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<Void> deleteSecurityZone(@PathVariable UUID id) {
        complianceService.deleteSecurityZone(id);
        return ResponseEntity.noContent().build();
    }

    // ── IcsAsset ─────────────────────────────────────────────────────────────

    @GetMapping("/ics-assets")
    public ResponseEntity<List<IcsAssetDto>> listIcsAssets() {
        return ResponseEntity.ok(complianceService.listIcsAssets());
    }

    @GetMapping("/ics-assets/{id}")
    public ResponseEntity<IcsAssetDto> getIcsAsset(@PathVariable UUID id) {
        return ResponseEntity.ok(complianceService.getIcsAsset(id));
    }

    @PostMapping("/ics-assets")
    @PreAuthorize(WRITE)
    public ResponseEntity<IcsAssetDto> createIcsAsset(@Validated(OnCreate.class) @RequestBody IcsAssetDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createIcsAsset(dto));
    }

    @PatchMapping("/ics-assets/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<IcsAssetDto> updateIcsAsset(
            @PathVariable UUID id, @Valid @RequestBody IcsAssetDto dto) {
        return ResponseEntity.ok(complianceService.updateIcsAsset(id, dto));
    }

    /** Full replace: every optional field absent from the body is cleared. */
    @PutMapping("/ics-assets/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<IcsAssetDto> replaceIcsAsset(
            @PathVariable UUID id, @Validated(OnCreate.class) @RequestBody IcsAssetDto dto) {
        return ResponseEntity.ok(complianceService.replaceIcsAsset(id, dto));
    }

    @DeleteMapping("/ics-assets/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<Void> deleteIcsAsset(@PathVariable UUID id) {
        complianceService.deleteIcsAsset(id);
        return ResponseEntity.noContent().build();
    }

    // ── PatchRecord ──────────────────────────────────────────────────────────

    @GetMapping("/patch-records")
    public ResponseEntity<PagedResponseDto<PatchRecordDto>> listPatchRecords(
            @RequestParam(required = false) UUID assetId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long offset,
            @PageableDefault(size = 20) Pageable pageable) {
        int effectiveLimit = (limit != null && limit > 0) ? limit : pageable.getPageSize();
        long effectiveOffset = (offset != null && offset >= 0)
                ? offset
                : (long) pageable.getPageNumber() * effectiveLimit;

        Pageable effectivePageable = PageRequest.of((int) (effectiveOffset / effectiveLimit), effectiveLimit, pageable.getSort());
        Page<PatchRecordDto> page = complianceService.listPatchRecords(assetId, effectivePageable);

        PagedResponseDto<PatchRecordDto> response = new PagedResponseDto<>();
        response.setTotal(page.getTotalElements());
        response.setLimit(effectiveLimit);
        response.setOffset(effectiveOffset);
        response.setItems(page.getContent());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/patch-records/{id}")
    public ResponseEntity<PatchRecordDto> getPatchRecord(@PathVariable UUID id) {
        return ResponseEntity.ok(complianceService.getPatchRecord(id));
    }

    @PostMapping("/patch-records")
    @PreAuthorize(WRITE)
    public ResponseEntity<PatchRecordDto> createPatchRecord(@Validated(OnCreate.class) @RequestBody PatchRecordDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createPatchRecord(dto));
    }

    @PatchMapping("/patch-records/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<PatchRecordDto> updatePatchRecord(
            @PathVariable UUID id, @Valid @RequestBody PatchRecordDto dto) {
        return ResponseEntity.ok(complianceService.updatePatchRecord(id, dto));
    }

    /** Full replace: every optional field absent from the body is cleared. */
    @PutMapping("/patch-records/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<PatchRecordDto> replacePatchRecord(
            @PathVariable UUID id, @Validated(OnCreate.class) @RequestBody PatchRecordDto dto) {
        return ResponseEntity.ok(complianceService.replacePatchRecord(id, dto));
    }

    @DeleteMapping("/patch-records/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<Void> deletePatchRecord(@PathVariable UUID id) {
        complianceService.deletePatchRecord(id);
        return ResponseEntity.noContent().build();
    }

    // ── PciSaqRecord ─────────────────────────────────────────────────────────

    @GetMapping("/pci-saq")
    public ResponseEntity<List<PciSaqRecordDto>> listPciSaqRecords() {
        return ResponseEntity.ok(complianceService.listPciSaqRecords());
    }

    @GetMapping("/pci-saq/{id}")
    public ResponseEntity<PciSaqRecordDto> getPciSaqRecord(@PathVariable UUID id) {
        return ResponseEntity.ok(complianceService.getPciSaqRecord(id));
    }

    @PostMapping("/pci-saq")
    @PreAuthorize(WRITE)
    public ResponseEntity<PciSaqRecordDto> upsertPciSaqRecord(@Validated(OnCreate.class) @RequestBody PciSaqRecordDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.upsertPciSaqRecord(dto));
    }

    @PatchMapping("/pci-saq/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<PciSaqRecordDto> updatePciSaqRecord(
            @PathVariable UUID id, @Valid @RequestBody PciSaqRecordDto dto) {
        return ResponseEntity.ok(complianceService.updatePciSaqRecord(id, dto));
    }

    /** Full replace: every optional field absent from the body is cleared. */
    @PutMapping("/pci-saq/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<PciSaqRecordDto> replacePciSaqRecord(
            @PathVariable UUID id, @Validated(OnCreate.class) @RequestBody PciSaqRecordDto dto) {
        return ResponseEntity.ok(complianceService.replacePciSaqRecord(id, dto));
    }

    // ── SlaMetric ────────────────────────────────────────────────────────────

    @GetMapping("/sla-metrics")
    public ResponseEntity<List<SlaMetricDto>> listSlaMetrics() {
        return ResponseEntity.ok(complianceService.listSlaMetrics());
    }

    @GetMapping("/sla-metrics/{id}")
    public ResponseEntity<SlaMetricDto> getSlaMetric(@PathVariable UUID id) {
        return ResponseEntity.ok(complianceService.getSlaMetric(id));
    }

    @PostMapping("/sla-metrics")
    @PreAuthorize(WRITE)
    public ResponseEntity<SlaMetricDto> createSlaMetric(@Validated(OnCreate.class) @RequestBody SlaMetricDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createSlaMetric(dto));
    }

    @PatchMapping("/sla-metrics/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<SlaMetricDto> updateSlaMetric(
            @PathVariable UUID id, @Valid @RequestBody SlaMetricDto dto) {
        return ResponseEntity.ok(complianceService.updateSlaMetric(id, dto));
    }

    /** Full replace: every optional field absent from the body is cleared. */
    @PutMapping("/sla-metrics/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<SlaMetricDto> replaceSlaMetric(
            @PathVariable UUID id, @Validated(OnCreate.class) @RequestBody SlaMetricDto dto) {
        return ResponseEntity.ok(complianceService.replaceSlaMetric(id, dto));
    }

    // ── VulnerabilityScan ────────────────────────────────────────────────────

    @GetMapping("/vulnerability-scans")
    public ResponseEntity<PagedResponseDto<VulnerabilityScanDto>> listVulnerabilityScans(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long offset,
            @PageableDefault(size = 20) Pageable pageable) {
        int effectiveLimit = (limit != null && limit > 0) ? limit : pageable.getPageSize();
        long effectiveOffset = (offset != null && offset >= 0)
                ? offset
                : (long) pageable.getPageNumber() * effectiveLimit;

        Pageable effectivePageable = PageRequest.of((int) (effectiveOffset / effectiveLimit), effectiveLimit, pageable.getSort());
        Page<VulnerabilityScanDto> page = complianceService.listVulnerabilityScans(effectivePageable);

        PagedResponseDto<VulnerabilityScanDto> response = new PagedResponseDto<>();
        response.setTotal(page.getTotalElements());
        response.setLimit(effectiveLimit);
        response.setOffset(effectiveOffset);
        response.setItems(page.getContent());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vulnerability-scans/{id}")
    public ResponseEntity<VulnerabilityScanDto> getVulnerabilityScan(@PathVariable UUID id) {
        return ResponseEntity.ok(complianceService.getVulnerabilityScan(id));
    }

    @PostMapping("/vulnerability-scans")
    @PreAuthorize(WRITE)
    public ResponseEntity<VulnerabilityScanDto> createVulnerabilityScan(
            @Validated(OnCreate.class) @RequestBody VulnerabilityScanDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createVulnerabilityScan(dto));
    }

    @PatchMapping("/vulnerability-scans/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<VulnerabilityScanDto> updateVulnerabilityScan(
            @PathVariable UUID id, @Valid @RequestBody VulnerabilityScanDto dto) {
        return ResponseEntity.ok(complianceService.updateVulnerabilityScan(id, dto));
    }

    /** Full replace: every optional field absent from the body is cleared. */
    @PutMapping("/vulnerability-scans/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<VulnerabilityScanDto> replaceVulnerabilityScan(
            @PathVariable UUID id, @Validated(OnCreate.class) @RequestBody VulnerabilityScanDto dto) {
        return ResponseEntity.ok(complianceService.replaceVulnerabilityScan(id, dto));
    }

    @DeleteMapping("/vulnerability-scans/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<Void> deleteVulnerabilityScan(@PathVariable UUID id) {
        complianceService.deleteVulnerabilityScan(id);
        return ResponseEntity.noContent().build();
    }

    // ── RegulatoryFiling ─────────────────────────────────────────────────────

    @GetMapping("/regulatory-filings")
    public ResponseEntity<List<RegulatoryFilingDto>> listRegulatoryFilings(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(complianceService.listRegulatoryFilings(status));
    }

    @GetMapping("/regulatory-filings/{id}")
    public ResponseEntity<RegulatoryFilingDto> getRegulatoryFiling(@PathVariable UUID id) {
        return ResponseEntity.ok(complianceService.getRegulatoryFiling(id));
    }

    @PostMapping("/regulatory-filings")
    @PreAuthorize(WRITE)
    public ResponseEntity<RegulatoryFilingDto> createRegulatoryFiling(
            @Validated(OnCreate.class) @RequestBody RegulatoryFilingDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createRegulatoryFiling(dto));
    }

    @PatchMapping("/regulatory-filings/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<RegulatoryFilingDto> updateRegulatoryFiling(
            @PathVariable UUID id, @Valid @RequestBody RegulatoryFilingDto dto) {
        return ResponseEntity.ok(complianceService.updateRegulatoryFiling(id, dto));
    }

    /** Full replace: every optional field absent from the body is cleared. */
    @PutMapping("/regulatory-filings/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<RegulatoryFilingDto> replaceRegulatoryFiling(
            @PathVariable UUID id, @Validated(OnCreate.class) @RequestBody RegulatoryFilingDto dto) {
        return ResponseEntity.ok(complianceService.replaceRegulatoryFiling(id, dto));
    }

    @DeleteMapping("/regulatory-filings/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<Void> deleteRegulatoryFiling(@PathVariable UUID id) {
        complianceService.deleteRegulatoryFiling(id);
        return ResponseEntity.noContent().build();
    }
}
