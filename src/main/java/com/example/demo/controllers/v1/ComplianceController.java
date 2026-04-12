package com.example.demo.controllers.v1;

import com.example.demo.dto.compliance.*;
import com.example.demo.dto.PagedResponseDto;
import com.example.demo.services.ComplianceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_AUDIT_LOGS','CONDUCT_AUDIT','MANAGE_SECURITY_SETTINGS','VIEW_COMPLIANCE','MANAGE_COMPLIANCE')")
public class ComplianceController {

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
    public ResponseEntity<ComplianceControlDto> createControl(@Valid @RequestBody ComplianceControlDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createControl(dto));
    }

    @PatchMapping("/controls/{id}")
    public ResponseEntity<ComplianceControlDto> updateControl(
            @PathVariable UUID id, @RequestBody ComplianceControlDto dto) {
        return ResponseEntity.ok(complianceService.updateControl(id, dto));
    }

    @DeleteMapping("/controls/{id}")
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
    public ResponseEntity<BogControlDto> createBogControl(@Valid @RequestBody BogControlDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createBogControl(dto));
    }

    @PatchMapping("/bog-controls/{id}")
    public ResponseEntity<BogControlDto> updateBogControl(
            @PathVariable UUID id, @RequestBody BogControlDto dto) {
        return ResponseEntity.ok(complianceService.updateBogControl(id, dto));
    }

    @DeleteMapping("/bog-controls/{id}")
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
    public ResponseEntity<RiskRegisterDto> createRisk(@Valid @RequestBody RiskRegisterDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createRisk(dto));
    }

    @PatchMapping("/risks/{id}")
    public ResponseEntity<RiskRegisterDto> updateRisk(
            @PathVariable UUID id, @RequestBody RiskRegisterDto dto) {
        return ResponseEntity.ok(complianceService.updateRisk(id, dto));
    }

    @DeleteMapping("/risks/{id}")
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
    public ResponseEntity<SecurityIncidentDto> createIncident(@Valid @RequestBody SecurityIncidentDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createIncident(dto));
    }

    @PatchMapping("/incidents/{id}")
    public ResponseEntity<SecurityIncidentDto> updateIncident(
            @PathVariable UUID id, @RequestBody SecurityIncidentDto dto) {
        return ResponseEntity.ok(complianceService.updateIncident(id, dto));
    }

    @DeleteMapping("/incidents/{id}")
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
    public ResponseEntity<SecurityPolicyDto> createPolicy(@Valid @RequestBody SecurityPolicyDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createPolicy(dto));
    }

    @PatchMapping("/policies/{id}")
    public ResponseEntity<SecurityPolicyDto> updatePolicy(
            @PathVariable UUID id, @RequestBody SecurityPolicyDto dto) {
        return ResponseEntity.ok(complianceService.updatePolicy(id, dto));
    }

    @DeleteMapping("/policies/{id}")
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
    public ResponseEntity<SecurityZoneDto> createSecurityZone(@Valid @RequestBody SecurityZoneDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createSecurityZone(dto));
    }

    @PatchMapping("/security-zones/{id}")
    public ResponseEntity<SecurityZoneDto> updateSecurityZone(
            @PathVariable UUID id, @RequestBody SecurityZoneDto dto) {
        return ResponseEntity.ok(complianceService.updateSecurityZone(id, dto));
    }

    @DeleteMapping("/security-zones/{id}")
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
    public ResponseEntity<IcsAssetDto> createIcsAsset(@Valid @RequestBody IcsAssetDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createIcsAsset(dto));
    }

    @PatchMapping("/ics-assets/{id}")
    public ResponseEntity<IcsAssetDto> updateIcsAsset(
            @PathVariable UUID id, @RequestBody IcsAssetDto dto) {
        return ResponseEntity.ok(complianceService.updateIcsAsset(id, dto));
    }

    @DeleteMapping("/ics-assets/{id}")
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
    public ResponseEntity<PatchRecordDto> createPatchRecord(@Valid @RequestBody PatchRecordDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createPatchRecord(dto));
    }

    @PatchMapping("/patch-records/{id}")
    public ResponseEntity<PatchRecordDto> updatePatchRecord(
            @PathVariable UUID id, @RequestBody PatchRecordDto dto) {
        return ResponseEntity.ok(complianceService.updatePatchRecord(id, dto));
    }

    @DeleteMapping("/patch-records/{id}")
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
    public ResponseEntity<PciSaqRecordDto> upsertPciSaqRecord(@Valid @RequestBody PciSaqRecordDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.upsertPciSaqRecord(dto));
    }

    @PatchMapping("/pci-saq/{id}")
    public ResponseEntity<PciSaqRecordDto> updatePciSaqRecord(
            @PathVariable UUID id, @RequestBody PciSaqRecordDto dto) {
        return ResponseEntity.ok(complianceService.updatePciSaqRecord(id, dto));
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
    public ResponseEntity<SlaMetricDto> createSlaMetric(@Valid @RequestBody SlaMetricDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createSlaMetric(dto));
    }

    @PatchMapping("/sla-metrics/{id}")
    public ResponseEntity<SlaMetricDto> updateSlaMetric(
            @PathVariable UUID id, @RequestBody SlaMetricDto dto) {
        return ResponseEntity.ok(complianceService.updateSlaMetric(id, dto));
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
    public ResponseEntity<VulnerabilityScanDto> createVulnerabilityScan(
            @Valid @RequestBody VulnerabilityScanDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createVulnerabilityScan(dto));
    }

    @PatchMapping("/vulnerability-scans/{id}")
    public ResponseEntity<VulnerabilityScanDto> updateVulnerabilityScan(
            @PathVariable UUID id, @RequestBody VulnerabilityScanDto dto) {
        return ResponseEntity.ok(complianceService.updateVulnerabilityScan(id, dto));
    }

    @DeleteMapping("/vulnerability-scans/{id}")
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
    public ResponseEntity<RegulatoryFilingDto> createRegulatoryFiling(
            @Valid @RequestBody RegulatoryFilingDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createRegulatoryFiling(dto));
    }

    @PatchMapping("/regulatory-filings/{id}")
    public ResponseEntity<RegulatoryFilingDto> updateRegulatoryFiling(
            @PathVariable UUID id, @RequestBody RegulatoryFilingDto dto) {
        return ResponseEntity.ok(complianceService.updateRegulatoryFiling(id, dto));
    }

    @DeleteMapping("/regulatory-filings/{id}")
    public ResponseEntity<Void> deleteRegulatoryFiling(@PathVariable UUID id) {
        complianceService.deleteRegulatoryFiling(id);
        return ResponseEntity.noContent().build();
    }
}
