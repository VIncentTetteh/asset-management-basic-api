package com.assetiq.services;

import com.assetiq.dto.compliance.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ComplianceService {

    // ── ComplianceControl ───────────────────────────────────────────────────
    List<ComplianceControlDto> listControls(String framework, String status);
    ComplianceControlDto getControl(UUID id);
    ComplianceControlDto createControl(ComplianceControlDto dto);
    ComplianceControlDto updateControl(UUID id, ComplianceControlDto dto);
    /** Full replace (PUT): an absent optional field is cleared. */
    ComplianceControlDto replaceControl(UUID id, ComplianceControlDto dto);
    void deleteControl(UUID id);

    // ── BogControl ──────────────────────────────────────────────────────────
    List<BogControlDto> listBogControls(String status);
    BogControlDto getBogControl(UUID id);
    BogControlDto createBogControl(BogControlDto dto);
    BogControlDto updateBogControl(UUID id, BogControlDto dto);
    /** Full replace (PUT): an absent optional field is cleared. */
    BogControlDto replaceBogControl(UUID id, BogControlDto dto);
    void deleteBogControl(UUID id);

    // ── RiskRegister ────────────────────────────────────────────────────────
    Page<RiskRegisterDto> listRisks(String status, Pageable pageable);
    RiskRegisterDto getRisk(UUID id);
    RiskRegisterDto createRisk(RiskRegisterDto dto);
    RiskRegisterDto updateRisk(UUID id, RiskRegisterDto dto);
    /** Full replace (PUT): an absent optional field is cleared. */
    RiskRegisterDto replaceRisk(UUID id, RiskRegisterDto dto);
    void deleteRisk(UUID id);

    // ── SecurityIncident ────────────────────────────────────────────────────
    Page<SecurityIncidentDto> listIncidents(Pageable pageable);
    SecurityIncidentDto getIncident(UUID id);
    SecurityIncidentDto createIncident(SecurityIncidentDto dto);
    SecurityIncidentDto updateIncident(UUID id, SecurityIncidentDto dto);
    /** Full replace (PUT): an absent optional field is cleared. */
    SecurityIncidentDto replaceIncident(UUID id, SecurityIncidentDto dto);
    void deleteIncident(UUID id);

    // ── SecurityPolicy ──────────────────────────────────────────────────────
    List<SecurityPolicyDto> listPolicies();
    SecurityPolicyDto getPolicy(UUID id);
    SecurityPolicyDto createPolicy(SecurityPolicyDto dto);
    SecurityPolicyDto updatePolicy(UUID id, SecurityPolicyDto dto);
    /** Full replace (PUT): an absent optional field is cleared. */
    SecurityPolicyDto replacePolicy(UUID id, SecurityPolicyDto dto);
    void deletePolicy(UUID id);

    // ── SecurityZone ────────────────────────────────────────────────────────
    List<SecurityZoneDto> listSecurityZones();
    SecurityZoneDto getSecurityZone(UUID id);
    SecurityZoneDto createSecurityZone(SecurityZoneDto dto);
    SecurityZoneDto updateSecurityZone(UUID id, SecurityZoneDto dto);
    /** Full replace (PUT): an absent optional field is cleared. */
    SecurityZoneDto replaceSecurityZone(UUID id, SecurityZoneDto dto);
    void deleteSecurityZone(UUID id);

    // ── IcsAsset ────────────────────────────────────────────────────────────
    List<IcsAssetDto> listIcsAssets();
    IcsAssetDto getIcsAsset(UUID id);
    IcsAssetDto createIcsAsset(IcsAssetDto dto);
    IcsAssetDto updateIcsAsset(UUID id, IcsAssetDto dto);
    /** Full replace (PUT): an absent optional field is cleared. */
    IcsAssetDto replaceIcsAsset(UUID id, IcsAssetDto dto);
    void deleteIcsAsset(UUID id);

    // ── PatchRecord ─────────────────────────────────────────────────────────
    Page<PatchRecordDto> listPatchRecords(UUID assetId, Pageable pageable);
    PatchRecordDto getPatchRecord(UUID id);
    PatchRecordDto createPatchRecord(PatchRecordDto dto);
    PatchRecordDto updatePatchRecord(UUID id, PatchRecordDto dto);
    /** Full replace (PUT): an absent optional field is cleared. */
    PatchRecordDto replacePatchRecord(UUID id, PatchRecordDto dto);
    void deletePatchRecord(UUID id);

    // ── PciSaqRecord ────────────────────────────────────────────────────────
    List<PciSaqRecordDto> listPciSaqRecords();
    PciSaqRecordDto getPciSaqRecord(UUID id);
    PciSaqRecordDto upsertPciSaqRecord(PciSaqRecordDto dto);
    PciSaqRecordDto updatePciSaqRecord(UUID id, PciSaqRecordDto dto);
    /** Full replace (PUT): an absent optional field is cleared. */
    PciSaqRecordDto replacePciSaqRecord(UUID id, PciSaqRecordDto dto);

    // ── SlaMetric ───────────────────────────────────────────────────────────
    List<SlaMetricDto> listSlaMetrics();
    SlaMetricDto getSlaMetric(UUID id);
    SlaMetricDto createSlaMetric(SlaMetricDto dto);
    SlaMetricDto updateSlaMetric(UUID id, SlaMetricDto dto);
    /** Full replace (PUT): an absent optional field is cleared. */
    SlaMetricDto replaceSlaMetric(UUID id, SlaMetricDto dto);

    /** Soft-deletes an SLA metric, freeing its month for a fresh record. */
    void deleteSlaMetric(UUID id);

    // ── VulnerabilityScan ───────────────────────────────────────────────────
    Page<VulnerabilityScanDto> listVulnerabilityScans(Pageable pageable);
    VulnerabilityScanDto getVulnerabilityScan(UUID id);
    VulnerabilityScanDto createVulnerabilityScan(VulnerabilityScanDto dto);
    VulnerabilityScanDto updateVulnerabilityScan(UUID id, VulnerabilityScanDto dto);
    /** Full replace (PUT): an absent optional field is cleared. */
    VulnerabilityScanDto replaceVulnerabilityScan(UUID id, VulnerabilityScanDto dto);
    void deleteVulnerabilityScan(UUID id);

    // ── RegulatoryFiling ────────────────────────────────────────────────────
    List<RegulatoryFilingDto> listRegulatoryFilings(String status);
    RegulatoryFilingDto getRegulatoryFiling(UUID id);
    RegulatoryFilingDto createRegulatoryFiling(RegulatoryFilingDto dto);
    RegulatoryFilingDto updateRegulatoryFiling(UUID id, RegulatoryFilingDto dto);
    /** Full replace (PUT): an absent optional field is cleared. */
    RegulatoryFilingDto replaceRegulatoryFiling(UUID id, RegulatoryFilingDto dto);
    void deleteRegulatoryFiling(UUID id);
}
