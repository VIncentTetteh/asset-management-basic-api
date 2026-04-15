package com.assetiq.services.impl;

import com.assetiq.dto.compliance.*;
import com.assetiq.models.Asset;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import com.assetiq.models.compliance.*;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.repositories.compliance.*;
import com.assetiq.services.ComplianceService;
import com.assetiq.services.TenantAwareService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ComplianceServiceImpl extends TenantAwareService implements ComplianceService {

    private final ComplianceControlRepository complianceControlRepository;
    private final BogControlRepository bogControlRepository;
    private final RiskRegisterRepository riskRegisterRepository;
    private final SecurityIncidentRepository securityIncidentRepository;
    private final SecurityPolicyRepository securityPolicyRepository;
    private final SecurityZoneRepository securityZoneRepository;
    private final IcsAssetRepository icsAssetRepository;
    private final PatchRecordRepository patchRecordRepository;
    private final PciSaqRecordRepository pciSaqRecordRepository;
    private final SlaMetricRepository slaMetricRepository;
    private final VulnerabilityScanRepository vulnerabilityScanRepository;
    private final RegulatoryFilingRepository regulatoryFilingRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;

    public ComplianceServiceImpl(
            OrganisationRepository organisationRepository,
            ComplianceControlRepository complianceControlRepository,
            BogControlRepository bogControlRepository,
            RiskRegisterRepository riskRegisterRepository,
            SecurityIncidentRepository securityIncidentRepository,
            SecurityPolicyRepository securityPolicyRepository,
            SecurityZoneRepository securityZoneRepository,
            IcsAssetRepository icsAssetRepository,
            PatchRecordRepository patchRecordRepository,
            PciSaqRecordRepository pciSaqRecordRepository,
            SlaMetricRepository slaMetricRepository,
            VulnerabilityScanRepository vulnerabilityScanRepository,
            RegulatoryFilingRepository regulatoryFilingRepository,
            UserRepository userRepository,
            AssetRepository assetRepository) {
        super(organisationRepository);
        this.complianceControlRepository = complianceControlRepository;
        this.bogControlRepository = bogControlRepository;
        this.riskRegisterRepository = riskRegisterRepository;
        this.securityIncidentRepository = securityIncidentRepository;
        this.securityPolicyRepository = securityPolicyRepository;
        this.securityZoneRepository = securityZoneRepository;
        this.icsAssetRepository = icsAssetRepository;
        this.patchRecordRepository = patchRecordRepository;
        this.pciSaqRecordRepository = pciSaqRecordRepository;
        this.slaMetricRepository = slaMetricRepository;
        this.vulnerabilityScanRepository = vulnerabilityScanRepository;
        this.regulatoryFilingRepository = regulatoryFilingRepository;
        this.userRepository = userRepository;
        this.assetRepository = assetRepository;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private User resolveUser(UUID userId) {
        if (userId == null) return null;
        Organisation org = requireTenantOrg();
        return userRepository.findByIdAndOrganisationAndDeletedAtIsNull(userId, org)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private Asset resolveAsset(UUID assetId) {
        Organisation org = requireTenantOrg();
        return assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));
    }

    // ── ComplianceControl ────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ComplianceControlDto> listControls(String framework, String status) {
        Organisation org = requireTenantOrg();
        List<ComplianceControl> controls;
        if (framework != null && status != null) {
            controls = complianceControlRepository.findByOrganisationAndFrameworkAndStatusAndDeletedAtIsNull(
                    org,
                    ComplianceFramework.valueOf(framework),
                    ControlStatus.valueOf(status));
        } else if (framework != null) {
            controls = complianceControlRepository.findByOrganisationAndFrameworkAndDeletedAtIsNull(
                    org, ComplianceFramework.valueOf(framework));
        } else {
            controls = complianceControlRepository.findByOrganisationAndDeletedAtIsNull(org);
        }
        return controls.stream().map(this::toControlDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ComplianceControlDto getControl(UUID id) {
        Organisation org = requireTenantOrg();
        return toControlDto(complianceControlRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Compliance control not found")));
    }

    @Override
    public ComplianceControlDto createControl(ComplianceControlDto dto) {
        Organisation org = requireTenantOrg();
        ComplianceControl control = new ComplianceControl();
        control.setOrganisation(org);
        applyControlFields(control, dto);
        return toControlDto(complianceControlRepository.save(control));
    }

    @Override
    public ComplianceControlDto updateControl(UUID id, ComplianceControlDto dto) {
        Organisation org = requireTenantOrg();
        ComplianceControl control = complianceControlRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Compliance control not found"));
        applyControlFields(control, dto);
        return toControlDto(complianceControlRepository.save(control));
    }

    @Override
    public void deleteControl(UUID id) {
        Organisation org = requireTenantOrg();
        ComplianceControl control = complianceControlRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Compliance control not found"));
        control.setDeletedAt(Instant.now());
        complianceControlRepository.save(control);
    }

    private void applyControlFields(ComplianceControl c, ComplianceControlDto dto) {
        if (dto.getFramework() != null) c.setFramework(dto.getFramework());
        if (dto.getControlRef() != null) c.setControlRef(dto.getControlRef());
        if (dto.getControlName() != null) c.setControlName(dto.getControlName());
        if (dto.getControlDescription() != null) c.setControlDescription(dto.getControlDescription());
        if (dto.getStatus() != null) c.setStatus(dto.getStatus());
        if (dto.getJustification() != null) c.setJustification(dto.getJustification());
        if (dto.getEvidenceUrl() != null) c.setEvidenceUrl(dto.getEvidenceUrl());
        if (dto.getGapDescription() != null) c.setGapDescription(dto.getGapDescription());
        if (dto.getRemediationPlan() != null) c.setRemediationPlan(dto.getRemediationPlan());
        if (dto.getOwnerId() != null) c.setOwner(resolveUser(dto.getOwnerId()));
        if (dto.getReviewDueDate() != null) c.setReviewDueDate(dto.getReviewDueDate());
        if (dto.getLastReviewedAt() != null) c.setLastReviewedAt(dto.getLastReviewedAt());
        if (dto.getLastReviewedByEmail() != null) c.setLastReviewedByEmail(dto.getLastReviewedByEmail());
    }

    private ComplianceControlDto toControlDto(ComplianceControl c) {
        ComplianceControlDto dto = new ComplianceControlDto();
        dto.setId(c.getId());
        dto.setOrganisationId(c.getOrganisation().getId());
        dto.setFramework(c.getFramework());
        dto.setControlRef(c.getControlRef());
        dto.setControlName(c.getControlName());
        dto.setControlDescription(c.getControlDescription());
        dto.setStatus(c.getStatus());
        dto.setJustification(c.getJustification());
        dto.setEvidenceUrl(c.getEvidenceUrl());
        dto.setGapDescription(c.getGapDescription());
        dto.setRemediationPlan(c.getRemediationPlan());
        if (c.getOwner() != null) {
            dto.setOwnerId(c.getOwner().getId());
            dto.setOwnerEmail(c.getOwner().getEmail());
        }
        dto.setReviewDueDate(c.getReviewDueDate());
        dto.setLastReviewedAt(c.getLastReviewedAt());
        dto.setLastReviewedByEmail(c.getLastReviewedByEmail());
        dto.setCreatedAt(c.getCreatedAt());
        dto.setUpdatedAt(c.getUpdatedAt());
        return dto;
    }

    // ── BogControl ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<BogControlDto> listBogControls(String status) {
        Organisation org = requireTenantOrg();
        List<BogControl> controls = status != null
                ? bogControlRepository.findByOrganisationAndStatusAndDeletedAtIsNull(org, ControlStatus.valueOf(status))
                : bogControlRepository.findByOrganisationAndDeletedAtIsNull(org);
        return controls.stream().map(this::toBogControlDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public BogControlDto getBogControl(UUID id) {
        Organisation org = requireTenantOrg();
        return toBogControlDto(bogControlRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("BOG control not found")));
    }

    @Override
    public BogControlDto createBogControl(BogControlDto dto) {
        Organisation org = requireTenantOrg();
        BogControl control = new BogControl();
        control.setOrganisation(org);
        applyBogControlFields(control, dto);
        return toBogControlDto(bogControlRepository.save(control));
    }

    @Override
    public BogControlDto updateBogControl(UUID id, BogControlDto dto) {
        Organisation org = requireTenantOrg();
        BogControl control = bogControlRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("BOG control not found"));
        applyBogControlFields(control, dto);
        return toBogControlDto(bogControlRepository.save(control));
    }

    @Override
    public void deleteBogControl(UUID id) {
        Organisation org = requireTenantOrg();
        BogControl control = bogControlRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("BOG control not found"));
        control.setDeletedAt(Instant.now());
        bogControlRepository.save(control);
    }

    private void applyBogControlFields(BogControl c, BogControlDto dto) {
        if (dto.getDirectiveRef() != null) c.setDirectiveRef(dto.getDirectiveRef());
        if (dto.getRequirement() != null) c.setRequirement(dto.getRequirement());
        if (dto.getStatus() != null) c.setStatus(dto.getStatus());
        if (dto.getEvidenceUrl() != null) c.setEvidenceUrl(dto.getEvidenceUrl());
        if (dto.getGapDescription() != null) c.setGapDescription(dto.getGapDescription());
        if (dto.getRemediationPlan() != null) c.setRemediationPlan(dto.getRemediationPlan());
        if (dto.getTargetDate() != null) c.setTargetDate(dto.getTargetDate());
        if (dto.getOwnerId() != null) c.setOwner(resolveUser(dto.getOwnerId()));
    }

    private BogControlDto toBogControlDto(BogControl c) {
        BogControlDto dto = new BogControlDto();
        dto.setId(c.getId());
        dto.setOrganisationId(c.getOrganisation().getId());
        dto.setDirectiveRef(c.getDirectiveRef());
        dto.setRequirement(c.getRequirement());
        dto.setStatus(c.getStatus());
        dto.setEvidenceUrl(c.getEvidenceUrl());
        dto.setGapDescription(c.getGapDescription());
        dto.setRemediationPlan(c.getRemediationPlan());
        dto.setTargetDate(c.getTargetDate());
        if (c.getOwner() != null) {
            dto.setOwnerId(c.getOwner().getId());
            dto.setOwnerEmail(c.getOwner().getEmail());
        }
        dto.setCreatedAt(c.getCreatedAt());
        dto.setUpdatedAt(c.getUpdatedAt());
        return dto;
    }

    // ── RiskRegister ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<RiskRegisterDto> listRisks(String status, Pageable pageable) {
        Organisation org = requireTenantOrg();
        if (status != null) {
            return riskRegisterRepository.findByOrganisationAndStatusAndDeletedAtIsNull(
                    org, RiskRegister.RiskStatus.valueOf(status))
                    .stream().map(this::toRiskDto)
                    .collect(Collectors.collectingAndThen(Collectors.toList(),
                            list -> new org.springframework.data.domain.PageImpl<>(list, pageable, list.size())));
        }
        return riskRegisterRepository.findByOrganisationAndDeletedAtIsNull(org, pageable)
                .map(this::toRiskDto);
    }

    @Override
    @Transactional(readOnly = true)
    public RiskRegisterDto getRisk(UUID id) {
        Organisation org = requireTenantOrg();
        return toRiskDto(riskRegisterRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Risk not found")));
    }

    @Override
    public RiskRegisterDto createRisk(RiskRegisterDto dto) {
        Organisation org = requireTenantOrg();
        RiskRegister risk = new RiskRegister();
        risk.setOrganisation(org);
        applyRiskFields(risk, dto);
        return toRiskDto(riskRegisterRepository.save(risk));
    }

    @Override
    public RiskRegisterDto updateRisk(UUID id, RiskRegisterDto dto) {
        Organisation org = requireTenantOrg();
        RiskRegister risk = riskRegisterRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Risk not found"));
        applyRiskFields(risk, dto);
        return toRiskDto(riskRegisterRepository.save(risk));
    }

    @Override
    public void deleteRisk(UUID id) {
        Organisation org = requireTenantOrg();
        RiskRegister risk = riskRegisterRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Risk not found"));
        risk.setDeletedAt(Instant.now());
        riskRegisterRepository.save(risk);
    }

    private void applyRiskFields(RiskRegister r, RiskRegisterDto dto) {
        if (dto.getFramework() != null) r.setFramework(dto.getFramework());
        if (dto.getRiskId() != null) r.setRiskId(dto.getRiskId());
        if (dto.getTitle() != null) r.setTitle(dto.getTitle());
        if (dto.getDescription() != null) r.setDescription(dto.getDescription());
        if (dto.getLikelihood() != null) r.setLikelihood(dto.getLikelihood());
        if (dto.getImpact() != null) r.setImpact(dto.getImpact());
        if (dto.getTreatment() != null) r.setTreatment(dto.getTreatment());
        if (dto.getMitigationPlan() != null) r.setMitigationPlan(dto.getMitigationPlan());
        if (dto.getResidualRisk() != null) r.setResidualRisk(dto.getResidualRisk());
        if (dto.getStatus() != null) r.setStatus(dto.getStatus());
        if (dto.getOwnerId() != null) r.setOwner(resolveUser(dto.getOwnerId()));
        if (dto.getReviewDate() != null) r.setReviewDate(dto.getReviewDate());
    }

    private RiskRegisterDto toRiskDto(RiskRegister r) {
        RiskRegisterDto dto = new RiskRegisterDto();
        dto.setId(r.getId());
        dto.setOrganisationId(r.getOrganisation().getId());
        dto.setFramework(r.getFramework());
        dto.setRiskId(r.getRiskId());
        dto.setTitle(r.getTitle());
        dto.setDescription(r.getDescription());
        dto.setLikelihood(r.getLikelihood());
        dto.setImpact(r.getImpact());
        dto.setRiskScore(r.getRiskScore());
        dto.setTreatment(r.getTreatment());
        dto.setMitigationPlan(r.getMitigationPlan());
        dto.setResidualRisk(r.getResidualRisk());
        dto.setStatus(r.getStatus());
        if (r.getOwner() != null) {
            dto.setOwnerId(r.getOwner().getId());
            dto.setOwnerEmail(r.getOwner().getEmail());
        }
        dto.setReviewDate(r.getReviewDate());
        dto.setCreatedAt(r.getCreatedAt());
        dto.setUpdatedAt(r.getUpdatedAt());
        return dto;
    }

    // ── SecurityIncident ─────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<SecurityIncidentDto> listIncidents(Pageable pageable) {
        Organisation org = requireTenantOrg();
        return securityIncidentRepository.findByOrganisationAndDeletedAtIsNull(org, pageable)
                .map(this::toIncidentDto);
    }

    @Override
    @Transactional(readOnly = true)
    public SecurityIncidentDto getIncident(UUID id) {
        Organisation org = requireTenantOrg();
        return toIncidentDto(securityIncidentRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found")));
    }

    @Override
    public SecurityIncidentDto createIncident(SecurityIncidentDto dto) {
        Organisation org = requireTenantOrg();
        SecurityIncident incident = new SecurityIncident();
        incident.setOrganisation(org);
        applyIncidentFields(incident, dto);
        return toIncidentDto(securityIncidentRepository.save(incident));
    }

    @Override
    public SecurityIncidentDto updateIncident(UUID id, SecurityIncidentDto dto) {
        Organisation org = requireTenantOrg();
        SecurityIncident incident = securityIncidentRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found"));
        applyIncidentFields(incident, dto);
        return toIncidentDto(securityIncidentRepository.save(incident));
    }

    @Override
    public void deleteIncident(UUID id) {
        Organisation org = requireTenantOrg();
        SecurityIncident incident = securityIncidentRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found"));
        incident.setDeletedAt(Instant.now());
        securityIncidentRepository.save(incident);
    }

    private void applyIncidentFields(SecurityIncident i, SecurityIncidentDto dto) {
        if (dto.getTitle() != null) i.setTitle(dto.getTitle());
        if (dto.getDescription() != null) i.setDescription(dto.getDescription());
        if (dto.getSeverity() != null) i.setSeverity(dto.getSeverity());
        if (dto.getCategory() != null) i.setCategory(dto.getCategory());
        if (dto.getReportedById() != null) i.setReportedBy(resolveUser(dto.getReportedById()));
        if (dto.getAssignedToId() != null) i.setAssignedTo(resolveUser(dto.getAssignedToId()));
        if (dto.getDetectedAt() != null) i.setDetectedAt(dto.getDetectedAt());
        if (dto.getResolvedAt() != null) i.setResolvedAt(dto.getResolvedAt());
        if (dto.getRootCause() != null) i.setRootCause(dto.getRootCause());
        if (dto.getLessonsLearned() != null) i.setLessonsLearned(dto.getLessonsLearned());
        if (dto.getStatus() != null) i.setStatus(dto.getStatus());
    }

    private SecurityIncidentDto toIncidentDto(SecurityIncident i) {
        SecurityIncidentDto dto = new SecurityIncidentDto();
        dto.setId(i.getId());
        dto.setOrganisationId(i.getOrganisation().getId());
        dto.setTitle(i.getTitle());
        dto.setDescription(i.getDescription());
        dto.setSeverity(i.getSeverity());
        dto.setCategory(i.getCategory());
        if (i.getReportedBy() != null) {
            dto.setReportedById(i.getReportedBy().getId());
            dto.setReportedByEmail(i.getReportedBy().getEmail());
        }
        if (i.getAssignedTo() != null) {
            dto.setAssignedToId(i.getAssignedTo().getId());
            dto.setAssignedToEmail(i.getAssignedTo().getEmail());
        }
        dto.setDetectedAt(i.getDetectedAt());
        dto.setResolvedAt(i.getResolvedAt());
        dto.setRootCause(i.getRootCause());
        dto.setLessonsLearned(i.getLessonsLearned());
        dto.setStatus(i.getStatus());
        dto.setCreatedAt(i.getCreatedAt());
        dto.setUpdatedAt(i.getUpdatedAt());
        return dto;
    }

    // ── SecurityPolicy ───────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<SecurityPolicyDto> listPolicies() {
        return securityPolicyRepository.findByOrganisationAndDeletedAtIsNull(requireTenantOrg())
                .stream().map(this::toPolicyDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public SecurityPolicyDto getPolicy(UUID id) {
        Organisation org = requireTenantOrg();
        return toPolicyDto(securityPolicyRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found")));
    }

    @Override
    public SecurityPolicyDto createPolicy(SecurityPolicyDto dto) {
        Organisation org = requireTenantOrg();
        SecurityPolicy policy = new SecurityPolicy();
        policy.setOrganisation(org);
        applyPolicyFields(policy, dto);
        return toPolicyDto(securityPolicyRepository.save(policy));
    }

    @Override
    public SecurityPolicyDto updatePolicy(UUID id, SecurityPolicyDto dto) {
        Organisation org = requireTenantOrg();
        SecurityPolicy policy = securityPolicyRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found"));
        applyPolicyFields(policy, dto);
        return toPolicyDto(securityPolicyRepository.save(policy));
    }

    @Override
    public void deletePolicy(UUID id) {
        Organisation org = requireTenantOrg();
        SecurityPolicy policy = securityPolicyRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found"));
        policy.setDeletedAt(Instant.now());
        securityPolicyRepository.save(policy);
    }

    private void applyPolicyFields(SecurityPolicy p, SecurityPolicyDto dto) {
        if (dto.getTitle() != null) p.setTitle(dto.getTitle());
        if (dto.getVersion() != null) p.setVersion(dto.getVersion());
        if (dto.getDocumentUrl() != null) p.setDocumentUrl(dto.getDocumentUrl());
        if (dto.getOwnerId() != null) p.setOwner(resolveUser(dto.getOwnerId()));
        if (dto.getApprovedByEmail() != null) p.setApprovedByEmail(dto.getApprovedByEmail());
        if (dto.getEffectiveDate() != null) p.setEffectiveDate(dto.getEffectiveDate());
        if (dto.getReviewDueDate() != null) p.setReviewDueDate(dto.getReviewDueDate());
        if (dto.getStatus() != null) p.setStatus(dto.getStatus());
    }

    private SecurityPolicyDto toPolicyDto(SecurityPolicy p) {
        SecurityPolicyDto dto = new SecurityPolicyDto();
        dto.setId(p.getId());
        dto.setOrganisationId(p.getOrganisation().getId());
        dto.setTitle(p.getTitle());
        dto.setVersion(p.getVersion());
        dto.setDocumentUrl(p.getDocumentUrl());
        if (p.getOwner() != null) {
            dto.setOwnerId(p.getOwner().getId());
            dto.setOwnerEmail(p.getOwner().getEmail());
        }
        dto.setApprovedByEmail(p.getApprovedByEmail());
        dto.setEffectiveDate(p.getEffectiveDate());
        dto.setReviewDueDate(p.getReviewDueDate());
        dto.setStatus(p.getStatus());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setUpdatedAt(p.getUpdatedAt());
        return dto;
    }

    // ── SecurityZone ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<SecurityZoneDto> listSecurityZones() {
        return securityZoneRepository.findByOrganisationAndDeletedAtIsNullOrderByPurdueLevel(requireTenantOrg())
                .stream().map(this::toZoneDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public SecurityZoneDto getSecurityZone(UUID id) {
        Organisation org = requireTenantOrg();
        return toZoneDto(securityZoneRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Security zone not found")));
    }

    @Override
    public SecurityZoneDto createSecurityZone(SecurityZoneDto dto) {
        Organisation org = requireTenantOrg();
        SecurityZone zone = new SecurityZone();
        zone.setOrganisation(org);
        applyZoneFields(zone, dto);
        return toZoneDto(securityZoneRepository.save(zone));
    }

    @Override
    public SecurityZoneDto updateSecurityZone(UUID id, SecurityZoneDto dto) {
        Organisation org = requireTenantOrg();
        SecurityZone zone = securityZoneRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Security zone not found"));
        applyZoneFields(zone, dto);
        return toZoneDto(securityZoneRepository.save(zone));
    }

    @Override
    public void deleteSecurityZone(UUID id) {
        Organisation org = requireTenantOrg();
        SecurityZone zone = securityZoneRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Security zone not found"));
        zone.setDeletedAt(Instant.now());
        securityZoneRepository.save(zone);
    }

    private void applyZoneFields(SecurityZone z, SecurityZoneDto dto) {
        if (dto.getName() != null) z.setName(dto.getName());
        if (dto.getPurdueLevel() != null) z.setPurdueLevel(dto.getPurdueLevel());
        if (dto.getDescription() != null) z.setDescription(dto.getDescription());
        if (dto.getAllowedProtocols() != null) z.setAllowedProtocols(dto.getAllowedProtocols());
        if (dto.getAssetCount() != null) z.setAssetCount(dto.getAssetCount());
        if (dto.getNetworkRange() != null) z.setNetworkRange(dto.getNetworkRange());
    }

    private SecurityZoneDto toZoneDto(SecurityZone z) {
        SecurityZoneDto dto = new SecurityZoneDto();
        dto.setId(z.getId());
        dto.setOrganisationId(z.getOrganisation().getId());
        dto.setName(z.getName());
        dto.setPurdueLevel(z.getPurdueLevel());
        dto.setDescription(z.getDescription());
        dto.setAllowedProtocols(z.getAllowedProtocols());
        dto.setAssetCount(z.getAssetCount());
        dto.setNetworkRange(z.getNetworkRange());
        dto.setCreatedAt(z.getCreatedAt());
        dto.setUpdatedAt(z.getUpdatedAt());
        return dto;
    }

    // ── IcsAsset ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<IcsAssetDto> listIcsAssets() {
        return icsAssetRepository.findByOrganisationAndDeletedAtIsNull(requireTenantOrg())
                .stream().map(this::toIcsAssetDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public IcsAssetDto getIcsAsset(UUID id) {
        Organisation org = requireTenantOrg();
        return toIcsAssetDto(icsAssetRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("ICS asset not found")));
    }

    @Override
    public IcsAssetDto createIcsAsset(IcsAssetDto dto) {
        Organisation org = requireTenantOrg();
        if (icsAssetRepository.findByAssetIdAndDeletedAtIsNull(dto.getAssetId()).isPresent()) {
            throw new IllegalArgumentException("ICS metadata already exists for this asset");
        }
        IcsAsset icsAsset = new IcsAsset();
        icsAsset.setOrganisation(org);
        icsAsset.setAsset(resolveAsset(dto.getAssetId()));
        applyIcsAssetFields(icsAsset, dto);
        return toIcsAssetDto(icsAssetRepository.save(icsAsset));
    }

    @Override
    public IcsAssetDto updateIcsAsset(UUID id, IcsAssetDto dto) {
        Organisation org = requireTenantOrg();
        IcsAsset icsAsset = icsAssetRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("ICS asset not found"));
        applyIcsAssetFields(icsAsset, dto);
        return toIcsAssetDto(icsAssetRepository.save(icsAsset));
    }

    @Override
    public void deleteIcsAsset(UUID id) {
        Organisation org = requireTenantOrg();
        IcsAsset icsAsset = icsAssetRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("ICS asset not found"));
        icsAsset.setDeletedAt(Instant.now());
        icsAssetRepository.save(icsAsset);
    }

    private void applyIcsAssetFields(IcsAsset a, IcsAssetDto dto) {
        if (dto.getSecurityZoneId() != null) {
            SecurityZone zone = securityZoneRepository.findByIdAndOrganisationAndDeletedAtIsNull(
                    dto.getSecurityZoneId(), a.getOrganisation())
                    .orElseThrow(() -> new IllegalArgumentException("Security zone not found"));
            a.setSecurityZone(zone);
        }
        if (dto.getFirmwareVersion() != null) a.setFirmwareVersion(dto.getFirmwareVersion());
        if (dto.getProtocol() != null) a.setProtocol(dto.getProtocol());
        if (dto.getVendorSupportStatus() != null) a.setVendorSupportStatus(dto.getVendorSupportStatus());
        if (dto.getLastPatchedAt() != null) a.setLastPatchedAt(dto.getLastPatchedAt());
        if (dto.getKnownVulnerabilities() != null) a.setKnownVulnerabilities(dto.getKnownVulnerabilities());
        if (dto.getIsolated() != null) a.setIsolated(dto.getIsolated());
        if (dto.getNotes() != null) a.setNotes(dto.getNotes());
    }

    private IcsAssetDto toIcsAssetDto(IcsAsset a) {
        IcsAssetDto dto = new IcsAssetDto();
        dto.setId(a.getId());
        dto.setOrganisationId(a.getOrganisation().getId());
        dto.setAssetId(a.getAsset().getId());
        dto.setAssetName(a.getAsset().getName());
        if (a.getSecurityZone() != null) {
            dto.setSecurityZoneId(a.getSecurityZone().getId());
            dto.setSecurityZoneName(a.getSecurityZone().getName());
        }
        dto.setFirmwareVersion(a.getFirmwareVersion());
        dto.setProtocol(a.getProtocol());
        dto.setVendorSupportStatus(a.getVendorSupportStatus());
        dto.setLastPatchedAt(a.getLastPatchedAt());
        dto.setKnownVulnerabilities(a.getKnownVulnerabilities());
        dto.setIsolated(a.getIsolated());
        dto.setNotes(a.getNotes());
        dto.setCreatedAt(a.getCreatedAt());
        dto.setUpdatedAt(a.getUpdatedAt());
        return dto;
    }

    // ── PatchRecord ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<PatchRecordDto> listPatchRecords(UUID assetId, Pageable pageable) {
        Organisation org = requireTenantOrg();
        if (assetId != null) {
            Asset asset = resolveAsset(assetId);
            return patchRecordRepository.findByOrganisationAndAssetAndDeletedAtIsNullOrderByAppliedAtDesc(
                    org, asset, pageable).map(this::toPatchRecordDto);
        }
        return patchRecordRepository.findByOrganisationAndDeletedAtIsNullOrderByAppliedAtDesc(org, pageable)
                .map(this::toPatchRecordDto);
    }

    @Override
    @Transactional(readOnly = true)
    public PatchRecordDto getPatchRecord(UUID id) {
        Organisation org = requireTenantOrg();
        return toPatchRecordDto(patchRecordRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Patch record not found")));
    }

    @Override
    public PatchRecordDto createPatchRecord(PatchRecordDto dto) {
        Organisation org = requireTenantOrg();
        PatchRecord record = new PatchRecord();
        record.setOrganisation(org);
        record.setAsset(resolveAsset(dto.getAssetId()));
        applyPatchRecordFields(record, dto);
        return toPatchRecordDto(patchRecordRepository.save(record));
    }

    @Override
    public PatchRecordDto updatePatchRecord(UUID id, PatchRecordDto dto) {
        Organisation org = requireTenantOrg();
        PatchRecord record = patchRecordRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Patch record not found"));
        applyPatchRecordFields(record, dto);
        return toPatchRecordDto(patchRecordRepository.save(record));
    }

    @Override
    public void deletePatchRecord(UUID id) {
        Organisation org = requireTenantOrg();
        PatchRecord record = patchRecordRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Patch record not found"));
        record.setDeletedAt(Instant.now());
        patchRecordRepository.save(record);
    }

    private void applyPatchRecordFields(PatchRecord r, PatchRecordDto dto) {
        if (dto.getPatchName() != null) r.setPatchName(dto.getPatchName());
        if (dto.getVersion() != null) r.setVersion(dto.getVersion());
        if (dto.getAppliedAt() != null) r.setAppliedAt(dto.getAppliedAt());
        if (dto.getAppliedByEmail() != null) r.setAppliedByEmail(dto.getAppliedByEmail());
        if (dto.getTestEnvironmentValidated() != null) r.setTestEnvironmentValidated(dto.getTestEnvironmentValidated());
        if (dto.getRollbackPlan() != null) r.setRollbackPlan(dto.getRollbackPlan());
        if (dto.getStatus() != null) r.setStatus(dto.getStatus());
        if (dto.getNotes() != null) r.setNotes(dto.getNotes());
    }

    private PatchRecordDto toPatchRecordDto(PatchRecord r) {
        PatchRecordDto dto = new PatchRecordDto();
        dto.setId(r.getId());
        dto.setOrganisationId(r.getOrganisation().getId());
        dto.setAssetId(r.getAsset().getId());
        dto.setAssetName(r.getAsset().getName());
        dto.setPatchName(r.getPatchName());
        dto.setVersion(r.getVersion());
        dto.setAppliedAt(r.getAppliedAt());
        dto.setAppliedByEmail(r.getAppliedByEmail());
        dto.setTestEnvironmentValidated(r.getTestEnvironmentValidated());
        dto.setRollbackPlan(r.getRollbackPlan());
        dto.setStatus(r.getStatus());
        dto.setNotes(r.getNotes());
        dto.setCreatedAt(r.getCreatedAt());
        dto.setUpdatedAt(r.getUpdatedAt());
        return dto;
    }

    // ── PciSaqRecord ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<PciSaqRecordDto> listPciSaqRecords() {
        return pciSaqRecordRepository.findByOrganisationAndDeletedAtIsNullOrderByRequirementNumber(requireTenantOrg())
                .stream().map(this::toPciSaqDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PciSaqRecordDto getPciSaqRecord(UUID id) {
        Organisation org = requireTenantOrg();
        return toPciSaqDto(pciSaqRecordRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("PCI SAQ record not found")));
    }

    @Override
    public PciSaqRecordDto upsertPciSaqRecord(PciSaqRecordDto dto) {
        Organisation org = requireTenantOrg();
        PciSaqRecord record = pciSaqRecordRepository
                .findByIdAndOrganisationAndDeletedAtIsNull(
                        dto.getId() != null ? dto.getId() : UUID.randomUUID(), org)
                .orElseGet(() -> {
                    PciSaqRecord r = new PciSaqRecord();
                    r.setOrganisation(org);
                    return r;
                });
        applyPciSaqFields(record, dto);
        return toPciSaqDto(pciSaqRecordRepository.save(record));
    }

    @Override
    public PciSaqRecordDto updatePciSaqRecord(UUID id, PciSaqRecordDto dto) {
        Organisation org = requireTenantOrg();
        PciSaqRecord record = pciSaqRecordRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("PCI SAQ record not found"));
        applyPciSaqFields(record, dto);
        return toPciSaqDto(pciSaqRecordRepository.save(record));
    }

    private void applyPciSaqFields(PciSaqRecord r, PciSaqRecordDto dto) {
        if (dto.getRequirementNumber() != null) r.setRequirementNumber(dto.getRequirementNumber());
        if (dto.getRequirementText() != null) r.setRequirementText(dto.getRequirementText());
        if (dto.getComplianceStatus() != null) r.setComplianceStatus(dto.getComplianceStatus());
        if (dto.getCompensatingControl() != null) r.setCompensatingControl(dto.getCompensatingControl());
        if (dto.getEvidenceUrl() != null) r.setEvidenceUrl(dto.getEvidenceUrl());
        if (dto.getTargetDate() != null) r.setTargetDate(dto.getTargetDate());
        if (dto.getNotes() != null) r.setNotes(dto.getNotes());
    }

    private PciSaqRecordDto toPciSaqDto(PciSaqRecord r) {
        PciSaqRecordDto dto = new PciSaqRecordDto();
        dto.setId(r.getId());
        dto.setOrganisationId(r.getOrganisation().getId());
        dto.setRequirementNumber(r.getRequirementNumber());
        dto.setRequirementText(r.getRequirementText());
        dto.setComplianceStatus(r.getComplianceStatus());
        dto.setCompensatingControl(r.getCompensatingControl());
        dto.setEvidenceUrl(r.getEvidenceUrl());
        dto.setTargetDate(r.getTargetDate());
        dto.setNotes(r.getNotes());
        dto.setCreatedAt(r.getCreatedAt());
        dto.setUpdatedAt(r.getUpdatedAt());
        return dto;
    }

    // ── SlaMetric ────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<SlaMetricDto> listSlaMetrics() {
        return slaMetricRepository.findByOrganisationAndDeletedAtIsNullOrderByYearDescMonthDesc(requireTenantOrg())
                .stream().map(this::toSlaMetricDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public SlaMetricDto getSlaMetric(UUID id) {
        Organisation org = requireTenantOrg();
        return toSlaMetricDto(slaMetricRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("SLA metric not found")));
    }

    @Override
    public SlaMetricDto createSlaMetric(SlaMetricDto dto) {
        Organisation org = requireTenantOrg();
        slaMetricRepository.findByOrganisationAndYearAndMonthAndDeletedAtIsNull(org, dto.getYear(), dto.getMonth())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "SLA metric already exists for " + dto.getYear() + "-" + dto.getMonth());
                });
        SlaMetric metric = new SlaMetric();
        metric.setOrganisation(org);
        applySlaMetricFields(metric, dto);
        return toSlaMetricDto(slaMetricRepository.save(metric));
    }

    @Override
    public SlaMetricDto updateSlaMetric(UUID id, SlaMetricDto dto) {
        Organisation org = requireTenantOrg();
        SlaMetric metric = slaMetricRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("SLA metric not found"));
        applySlaMetricFields(metric, dto);
        return toSlaMetricDto(slaMetricRepository.save(metric));
    }

    private void applySlaMetricFields(SlaMetric m, SlaMetricDto dto) {
        if (dto.getMonth() != null) m.setMonth(dto.getMonth());
        if (dto.getYear() != null) m.setYear(dto.getYear());
        if (dto.getUptimePercent() != null) m.setUptimePercent(dto.getUptimePercent());
        if (dto.getPlannedDowntimeMinutes() != null) m.setPlannedDowntimeMinutes(dto.getPlannedDowntimeMinutes());
        if (dto.getUnplannedDowntimeMinutes() != null) m.setUnplannedDowntimeMinutes(dto.getUnplannedDowntimeMinutes());
        if (dto.getIncidentCount() != null) m.setIncidentCount(dto.getIncidentCount());
        if (dto.getRtoMinutes() != null) m.setRtoMinutes(dto.getRtoMinutes());
        if (dto.getRpoMinutes() != null) m.setRpoMinutes(dto.getRpoMinutes());
        if (dto.getSlaBreached() != null) m.setSlaBreached(dto.getSlaBreached());
        if (dto.getNotes() != null) m.setNotes(dto.getNotes());
    }

    private SlaMetricDto toSlaMetricDto(SlaMetric m) {
        SlaMetricDto dto = new SlaMetricDto();
        dto.setId(m.getId());
        dto.setOrganisationId(m.getOrganisation().getId());
        dto.setMonth(m.getMonth());
        dto.setYear(m.getYear());
        dto.setUptimePercent(m.getUptimePercent());
        dto.setPlannedDowntimeMinutes(m.getPlannedDowntimeMinutes());
        dto.setUnplannedDowntimeMinutes(m.getUnplannedDowntimeMinutes());
        dto.setIncidentCount(m.getIncidentCount());
        dto.setRtoMinutes(m.getRtoMinutes());
        dto.setRpoMinutes(m.getRpoMinutes());
        dto.setSlaBreached(m.getSlaBreached());
        dto.setNotes(m.getNotes());
        dto.setCreatedAt(m.getCreatedAt());
        dto.setUpdatedAt(m.getUpdatedAt());
        return dto;
    }

    // ── VulnerabilityScan ────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<VulnerabilityScanDto> listVulnerabilityScans(Pageable pageable) {
        return vulnerabilityScanRepository.findByOrganisationAndDeletedAtIsNullOrderByScanDateDesc(
                requireTenantOrg(), pageable).map(this::toVulnScanDto);
    }

    @Override
    @Transactional(readOnly = true)
    public VulnerabilityScanDto getVulnerabilityScan(UUID id) {
        Organisation org = requireTenantOrg();
        return toVulnScanDto(vulnerabilityScanRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Vulnerability scan not found")));
    }

    @Override
    public VulnerabilityScanDto createVulnerabilityScan(VulnerabilityScanDto dto) {
        Organisation org = requireTenantOrg();
        VulnerabilityScan scan = new VulnerabilityScan();
        scan.setOrganisation(org);
        applyVulnScanFields(scan, dto);
        return toVulnScanDto(vulnerabilityScanRepository.save(scan));
    }

    @Override
    public VulnerabilityScanDto updateVulnerabilityScan(UUID id, VulnerabilityScanDto dto) {
        Organisation org = requireTenantOrg();
        VulnerabilityScan scan = vulnerabilityScanRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Vulnerability scan not found"));
        applyVulnScanFields(scan, dto);
        return toVulnScanDto(vulnerabilityScanRepository.save(scan));
    }

    @Override
    public void deleteVulnerabilityScan(UUID id) {
        Organisation org = requireTenantOrg();
        VulnerabilityScan scan = vulnerabilityScanRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Vulnerability scan not found"));
        scan.setDeletedAt(Instant.now());
        vulnerabilityScanRepository.save(scan);
    }

    private void applyVulnScanFields(VulnerabilityScan s, VulnerabilityScanDto dto) {
        if (dto.getScanDate() != null) s.setScanDate(dto.getScanDate());
        if (dto.getScannerTool() != null) s.setScannerTool(dto.getScannerTool());
        if (dto.getScanType() != null) s.setScanType(dto.getScanType());
        if (dto.getCriticalCount() != null) s.setCriticalCount(dto.getCriticalCount());
        if (dto.getHighCount() != null) s.setHighCount(dto.getHighCount());
        if (dto.getMediumCount() != null) s.setMediumCount(dto.getMediumCount());
        if (dto.getLowCount() != null) s.setLowCount(dto.getLowCount());
        if (dto.getStatus() != null) s.setStatus(dto.getStatus());
        if (dto.getReportUrl() != null) s.setReportUrl(dto.getReportUrl());
        if (dto.getNextScanDue() != null) s.setNextScanDue(dto.getNextScanDue());
        if (dto.getNotes() != null) s.setNotes(dto.getNotes());
    }

    private VulnerabilityScanDto toVulnScanDto(VulnerabilityScan s) {
        VulnerabilityScanDto dto = new VulnerabilityScanDto();
        dto.setId(s.getId());
        dto.setOrganisationId(s.getOrganisation().getId());
        dto.setScanDate(s.getScanDate());
        dto.setScannerTool(s.getScannerTool());
        dto.setScanType(s.getScanType());
        dto.setCriticalCount(s.getCriticalCount());
        dto.setHighCount(s.getHighCount());
        dto.setMediumCount(s.getMediumCount());
        dto.setLowCount(s.getLowCount());
        dto.setStatus(s.getStatus());
        dto.setReportUrl(s.getReportUrl());
        dto.setNextScanDue(s.getNextScanDue());
        dto.setNotes(s.getNotes());
        dto.setCreatedAt(s.getCreatedAt());
        dto.setUpdatedAt(s.getUpdatedAt());
        return dto;
    }

    // ── RegulatoryFiling ─────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<RegulatoryFilingDto> listRegulatoryFilings(String status) {
        Organisation org = requireTenantOrg();
        List<RegulatoryFiling> filings = status != null
                ? regulatoryFilingRepository.findByOrganisationAndStatusAndDeletedAtIsNull(
                        org, RegulatoryFiling.FilingStatus.valueOf(status))
                : regulatoryFilingRepository.findByOrganisationAndDeletedAtIsNullOrderByDueDateAsc(org);
        return filings.stream().map(this::toFilingDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public RegulatoryFilingDto getRegulatoryFiling(UUID id) {
        Organisation org = requireTenantOrg();
        return toFilingDto(regulatoryFilingRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Regulatory filing not found")));
    }

    @Override
    public RegulatoryFilingDto createRegulatoryFiling(RegulatoryFilingDto dto) {
        Organisation org = requireTenantOrg();
        RegulatoryFiling filing = new RegulatoryFiling();
        filing.setOrganisation(org);
        applyFilingFields(filing, dto);
        return toFilingDto(regulatoryFilingRepository.save(filing));
    }

    @Override
    public RegulatoryFilingDto updateRegulatoryFiling(UUID id, RegulatoryFilingDto dto) {
        Organisation org = requireTenantOrg();
        RegulatoryFiling filing = regulatoryFilingRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Regulatory filing not found"));
        applyFilingFields(filing, dto);
        return toFilingDto(regulatoryFilingRepository.save(filing));
    }

    @Override
    public void deleteRegulatoryFiling(UUID id) {
        Organisation org = requireTenantOrg();
        RegulatoryFiling filing = regulatoryFilingRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Regulatory filing not found"));
        filing.setDeletedAt(Instant.now());
        regulatoryFilingRepository.save(filing);
    }

    private void applyFilingFields(RegulatoryFiling f, RegulatoryFilingDto dto) {
        if (dto.getFilingType() != null) f.setFilingType(dto.getFilingType());
        if (dto.getRegulator() != null) f.setRegulator(dto.getRegulator());
        if (dto.getDueDate() != null) f.setDueDate(dto.getDueDate());
        if (dto.getSubmittedAt() != null) f.setSubmittedAt(dto.getSubmittedAt());
        if (dto.getReference() != null) f.setReference(dto.getReference());
        if (dto.getStatus() != null) f.setStatus(dto.getStatus());
        if (dto.getNotes() != null) f.setNotes(dto.getNotes());
    }

    private RegulatoryFilingDto toFilingDto(RegulatoryFiling f) {
        RegulatoryFilingDto dto = new RegulatoryFilingDto();
        dto.setId(f.getId());
        dto.setOrganisationId(f.getOrganisation().getId());
        dto.setFilingType(f.getFilingType());
        dto.setRegulator(f.getRegulator());
        dto.setDueDate(f.getDueDate());
        dto.setSubmittedAt(f.getSubmittedAt());
        dto.setReference(f.getReference());
        dto.setStatus(f.getStatus());
        dto.setNotes(f.getNotes());
        dto.setCreatedAt(f.getCreatedAt());
        dto.setUpdatedAt(f.getUpdatedAt());
        return dto;
    }
}
