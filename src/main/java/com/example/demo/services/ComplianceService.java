package com.example.demo.services;

import com.example.demo.dto.compliance.*;
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
    void deleteControl(UUID id);

    // ── BogControl ──────────────────────────────────────────────────────────
    List<BogControlDto> listBogControls(String status);
    BogControlDto getBogControl(UUID id);
    BogControlDto createBogControl(BogControlDto dto);
    BogControlDto updateBogControl(UUID id, BogControlDto dto);
    void deleteBogControl(UUID id);

    // ── RiskRegister ────────────────────────────────────────────────────────
    Page<RiskRegisterDto> listRisks(String status, Pageable pageable);
    RiskRegisterDto getRisk(UUID id);
    RiskRegisterDto createRisk(RiskRegisterDto dto);
    RiskRegisterDto updateRisk(UUID id, RiskRegisterDto dto);
    void deleteRisk(UUID id);

    // ── SecurityIncident ────────────────────────────────────────────────────
    Page<SecurityIncidentDto> listIncidents(Pageable pageable);
    SecurityIncidentDto getIncident(UUID id);
    SecurityIncidentDto createIncident(SecurityIncidentDto dto);
    SecurityIncidentDto updateIncident(UUID id, SecurityIncidentDto dto);
    void deleteIncident(UUID id);

    // ── SecurityPolicy ──────────────────────────────────────────────────────
    List<SecurityPolicyDto> listPolicies();
    SecurityPolicyDto getPolicy(UUID id);
    SecurityPolicyDto createPolicy(SecurityPolicyDto dto);
    SecurityPolicyDto updatePolicy(UUID id, SecurityPolicyDto dto);
    void deletePolicy(UUID id);

    // ── SecurityZone ────────────────────────────────────────────────────────
    List<SecurityZoneDto> listSecurityZones();
    SecurityZoneDto getSecurityZone(UUID id);
    SecurityZoneDto createSecurityZone(SecurityZoneDto dto);
    SecurityZoneDto updateSecurityZone(UUID id, SecurityZoneDto dto);
    void deleteSecurityZone(UUID id);

    // ── IcsAsset ────────────────────────────────────────────────────────────
    List<IcsAssetDto> listIcsAssets();
    IcsAssetDto getIcsAsset(UUID id);
    IcsAssetDto createIcsAsset(IcsAssetDto dto);
    IcsAssetDto updateIcsAsset(UUID id, IcsAssetDto dto);
    void deleteIcsAsset(UUID id);

    // ── PatchRecord ─────────────────────────────────────────────────────────
    Page<PatchRecordDto> listPatchRecords(UUID assetId, Pageable pageable);
    PatchRecordDto getPatchRecord(UUID id);
    PatchRecordDto createPatchRecord(PatchRecordDto dto);
    PatchRecordDto updatePatchRecord(UUID id, PatchRecordDto dto);
    void deletePatchRecord(UUID id);

    // ── PciSaqRecord ────────────────────────────────────────────────────────
    List<PciSaqRecordDto> listPciSaqRecords();
    PciSaqRecordDto getPciSaqRecord(UUID id);
    PciSaqRecordDto upsertPciSaqRecord(PciSaqRecordDto dto);
    PciSaqRecordDto updatePciSaqRecord(UUID id, PciSaqRecordDto dto);

    // ── SlaMetric ───────────────────────────────────────────────────────────
    List<SlaMetricDto> listSlaMetrics();
    SlaMetricDto getSlaMetric(UUID id);
    SlaMetricDto createSlaMetric(SlaMetricDto dto);
    SlaMetricDto updateSlaMetric(UUID id, SlaMetricDto dto);

    // ── VulnerabilityScan ───────────────────────────────────────────────────
    Page<VulnerabilityScanDto> listVulnerabilityScans(Pageable pageable);
    VulnerabilityScanDto getVulnerabilityScan(UUID id);
    VulnerabilityScanDto createVulnerabilityScan(VulnerabilityScanDto dto);
    VulnerabilityScanDto updateVulnerabilityScan(UUID id, VulnerabilityScanDto dto);
    void deleteVulnerabilityScan(UUID id);

    // ── RegulatoryFiling ────────────────────────────────────────────────────
    List<RegulatoryFilingDto> listRegulatoryFilings(String status);
    RegulatoryFilingDto getRegulatoryFiling(UUID id);
    RegulatoryFilingDto createRegulatoryFiling(RegulatoryFilingDto dto);
    RegulatoryFilingDto updateRegulatoryFiling(UUID id, RegulatoryFilingDto dto);
    void deleteRegulatoryFiling(UUID id);
}
