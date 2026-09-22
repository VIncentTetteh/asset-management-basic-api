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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.assetiq.exceptions.DuplicateFieldException;
import com.assetiq.exceptions.FieldValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
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

    private Asset resolveAsset(UUID assetId) {
        Organisation org = requireTenantOrg();
        return assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));
    }

    // ── Field writers (PATCH vs PUT) ─────────────────────────────────────────

    /** Sets {@code value} when present; on a full replace an absent value clears the field. */
    private static <T> void put(boolean replace, T value, Consumer<T> setter) {
        if (value != null || replace) {
            setter.accept(value);
        }
    }

    /** Like {@link #put}, but a cleared NOT NULL column takes {@code whenCleared} instead of null. */
    private static <T> void putOr(boolean replace, T value, T whenCleared, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        } else if (replace) {
            setter.accept(whenCleared);
        }
    }

    /** Links a user of this organisation by id (a field error when unknown); a full replace unlinks. */
    private void putUser(boolean replace, String field, UUID userId, Consumer<User> setter) {
        if (userId != null) {
            Organisation org = requireTenantOrg();
            setter.accept(userRepository.findByIdAndOrganisationAndDeletedAtIsNull(userId, org)
                    .orElseThrow(() -> new FieldValidationException(field, "User not found in this organisation")));
        } else if (replace) {
            setter.accept(null);
        }
    }

    /** The email of a user in this organisation, as stored (a field error when no such user). */
    private String requireOrgUserEmail(String field, String email) {
        Organisation org = requireTenantOrg();
        return userRepository.findByEmailAndOrganisationId(email.trim(), org.getId())
                .map(User::getEmail)
                .orElseThrow(() -> new FieldValidationException(field, "No user with this email in this organisation"));
    }

    /** The signed-in user's email, recorded as the actor on reviews and applied patches. */
    private static String currentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() ? auth.getName() : null;
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
        applyControlFields(control, dto, false);
        return toControlDto(complianceControlRepository.save(control));
    }

    @Override
    public ComplianceControlDto updateControl(UUID id, ComplianceControlDto dto) {
        return writeControl(id, dto, false);
    }

    @Override
    public ComplianceControlDto replaceControl(UUID id, ComplianceControlDto dto) {
        return writeControl(id, dto, true);
    }

    /** PATCH ({@code replace} false: null leaves a field unchanged) or PUT (null clears it). */
    private ComplianceControlDto writeControl(UUID id, ComplianceControlDto dto, boolean replace) {
        Organisation org = requireTenantOrg();
        ComplianceControl control = complianceControlRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Compliance control not found"));
        applyControlFields(control, dto, replace);
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

    private void applyControlFields(ComplianceControl c, ComplianceControlDto dto, boolean replace) {
        if (dto.getFramework() != null) c.setFramework(dto.getFramework());
        if (dto.getControlRef() != null) c.setControlRef(dto.getControlRef());
        if (dto.getControlName() != null) c.setControlName(dto.getControlName());
        put(replace, dto.getControlDescription(), c::setControlDescription);
        if (dto.getStatus() != null) c.setStatus(dto.getStatus());
        put(replace, dto.getJustification(), c::setJustification);
        put(replace, dto.getEvidenceUrl(), c::setEvidenceUrl);
        put(replace, dto.getGapDescription(), c::setGapDescription);
        put(replace, dto.getRemediationPlan(), c::setRemediationPlan);
        putUser(replace, "ownerId", dto.getOwnerId(), c::setOwner);
        put(replace, dto.getReviewDueDate(), c::setReviewDueDate);
        // Who reviewed is the signed-in user who recorded the review, never client input.
        Instant reviewed = dto.getLastReviewedAt();
        if (reviewed != null && !reviewed.equals(c.getLastReviewedAt())) {
            c.setLastReviewedAt(reviewed);
            c.setLastReviewedByEmail(currentUserEmail());
        } else if (reviewed == null && replace) {
            c.setLastReviewedAt(null);
            c.setLastReviewedByEmail(null);
        }
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
        assertDirectiveRefFree(org, dto.getDirectiveRef(), null);
        BogControl control = new BogControl();
        control.setOrganisation(org);
        applyBogControlFields(control, dto, false);
        return toBogControlDto(bogControlRepository.save(control));
    }

    @Override
    public BogControlDto updateBogControl(UUID id, BogControlDto dto) {
        return writeBogControl(id, dto, false);
    }

    @Override
    public BogControlDto replaceBogControl(UUID id, BogControlDto dto) {
        return writeBogControl(id, dto, true);
    }

    /** PATCH ({@code replace} false: null leaves a field unchanged) or PUT (null clears it). */
    private BogControlDto writeBogControl(UUID id, BogControlDto dto, boolean replace) {
        Organisation org = requireTenantOrg();
        BogControl control = bogControlRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("BOG control not found"));
        assertDirectiveRefFree(org, dto.getDirectiveRef(), control.getId());
        applyBogControlFields(control, dto, replace);
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

    /** A BoG directive reference appears once per organisation (V46 uq_bog_control_org_ref_live). */
    private void assertDirectiveRefFree(Organisation org, String directiveRef, UUID selfId) {
        if (directiveRef == null) return;
        bogControlRepository.findByOrganisationAndDirectiveRefAndDeletedAtIsNull(org, directiveRef)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw new DuplicateFieldException("directiveRef",
                            "A BoG control with directive " + directiveRef + " already exists");
                });
    }

    private void applyBogControlFields(BogControl c, BogControlDto dto, boolean replace) {
        if (dto.getDirectiveRef() != null) c.setDirectiveRef(dto.getDirectiveRef());
        if (dto.getRequirement() != null) c.setRequirement(dto.getRequirement());
        if (dto.getStatus() != null) c.setStatus(dto.getStatus());
        put(replace, dto.getEvidenceUrl(), c::setEvidenceUrl);
        put(replace, dto.getGapDescription(), c::setGapDescription);
        put(replace, dto.getRemediationPlan(), c::setRemediationPlan);
        put(replace, dto.getTargetDate(), c::setTargetDate);
        putUser(replace, "ownerId", dto.getOwnerId(), c::setOwner);
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
        applyRiskFields(risk, dto, false);
        return toRiskDto(riskRegisterRepository.save(risk));
    }

    @Override
    public RiskRegisterDto updateRisk(UUID id, RiskRegisterDto dto) {
        return writeRisk(id, dto, false);
    }

    @Override
    public RiskRegisterDto replaceRisk(UUID id, RiskRegisterDto dto) {
        return writeRisk(id, dto, true);
    }

    /** PATCH ({@code replace} false: null leaves a field unchanged) or PUT (null clears it). */
    private RiskRegisterDto writeRisk(UUID id, RiskRegisterDto dto, boolean replace) {
        Organisation org = requireTenantOrg();
        RiskRegister risk = riskRegisterRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Risk not found"));
        applyRiskFields(risk, dto, replace);
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

    private void applyRiskFields(RiskRegister r, RiskRegisterDto dto, boolean replace) {
        put(replace, dto.getFramework(), r::setFramework);
        put(replace, dto.getRiskId(), r::setRiskId);
        if (dto.getTitle() != null) r.setTitle(dto.getTitle());
        put(replace, dto.getDescription(), r::setDescription);
        if (dto.getLikelihood() != null) r.setLikelihood(dto.getLikelihood());
        if (dto.getImpact() != null) r.setImpact(dto.getImpact());
        put(replace, dto.getTreatment(), r::setTreatment);
        put(replace, dto.getMitigationPlan(), r::setMitigationPlan);
        put(replace, dto.getResidualRisk(), r::setResidualRisk);
        if (dto.getStatus() != null) r.setStatus(dto.getStatus());
        putUser(replace, "ownerId", dto.getOwnerId(), r::setOwner);
        put(replace, dto.getReviewDate(), r::setReviewDate);
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
        applyIncidentFields(incident, dto, false);
        checkIncidentTimeline(incident);
        return toIncidentDto(securityIncidentRepository.save(incident));
    }

    @Override
    public SecurityIncidentDto updateIncident(UUID id, SecurityIncidentDto dto) {
        return writeIncident(id, dto, false);
    }

    @Override
    public SecurityIncidentDto replaceIncident(UUID id, SecurityIncidentDto dto) {
        return writeIncident(id, dto, true);
    }

    /** PATCH ({@code replace} false: null leaves a field unchanged) or PUT (null clears it). */
    private SecurityIncidentDto writeIncident(UUID id, SecurityIncidentDto dto, boolean replace) {
        Organisation org = requireTenantOrg();
        SecurityIncident incident = securityIncidentRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found"));
        applyIncidentFields(incident, dto, replace);
        checkIncidentTimeline(incident);
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

    /**
     * A resolved or closed incident has a resolution date (stamped now when none was
     * given), and an incident cannot be resolved before it was detected.
     */
    private static void checkIncidentTimeline(SecurityIncident i) {
        boolean done = i.getStatus() == SecurityIncident.IncidentStatus.RESOLVED
                || i.getStatus() == SecurityIncident.IncidentStatus.CLOSED;
        if (done && i.getResolvedAt() == null) {
            i.setResolvedAt(Instant.now());
        }
        if (i.getResolvedAt() != null && i.getDetectedAt() != null && i.getResolvedAt().isBefore(i.getDetectedAt())) {
            throw new FieldValidationException("resolvedAt", "must be on or after the detection date");
        }
    }

    private void applyIncidentFields(SecurityIncident i, SecurityIncidentDto dto, boolean replace) {
        if (dto.getTitle() != null) i.setTitle(dto.getTitle());
        put(replace, dto.getDescription(), i::setDescription);
        if (dto.getSeverity() != null) i.setSeverity(dto.getSeverity());
        put(replace, dto.getCategory(), i::setCategory);
        putUser(replace, "reportedById", dto.getReportedById(), i::setReportedBy);
        putUser(replace, "assignedToId", dto.getAssignedToId(), i::setAssignedTo);
        put(replace, dto.getDetectedAt(), i::setDetectedAt);
        put(replace, dto.getResolvedAt(), i::setResolvedAt);
        put(replace, dto.getRootCause(), i::setRootCause);
        put(replace, dto.getLessonsLearned(), i::setLessonsLearned);
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
        applyPolicyFields(policy, dto, false);
        return toPolicyDto(securityPolicyRepository.save(policy));
    }

    @Override
    public SecurityPolicyDto updatePolicy(UUID id, SecurityPolicyDto dto) {
        return writePolicy(id, dto, false);
    }

    @Override
    public SecurityPolicyDto replacePolicy(UUID id, SecurityPolicyDto dto) {
        return writePolicy(id, dto, true);
    }

    /** PATCH ({@code replace} false: null leaves a field unchanged) or PUT (null clears it). */
    private SecurityPolicyDto writePolicy(UUID id, SecurityPolicyDto dto, boolean replace) {
        Organisation org = requireTenantOrg();
        SecurityPolicy policy = securityPolicyRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found"));
        applyPolicyFields(policy, dto, replace);
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

    private void applyPolicyFields(SecurityPolicy p, SecurityPolicyDto dto, boolean replace) {
        if (dto.getTitle() != null) p.setTitle(dto.getTitle());
        put(replace, dto.getVersion(), p::setVersion);
        put(replace, dto.getDocumentUrl(), p::setDocumentUrl);
        putUser(replace, "ownerId", dto.getOwnerId(), p::setOwner);
        String approver = dto.getApprovedByEmail();
        if (approver != null && !approver.isBlank()) {
            p.setApprovedByEmail(requireOrgUserEmail("approvedByEmail", approver));
        } else if (approver != null || replace) {
            p.setApprovedByEmail(null);
        }
        put(replace, dto.getEffectiveDate(), p::setEffectiveDate);
        put(replace, dto.getReviewDueDate(), p::setReviewDueDate);
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
        applyZoneFields(zone, dto, false);
        return toZoneDto(securityZoneRepository.save(zone));
    }

    @Override
    public SecurityZoneDto updateSecurityZone(UUID id, SecurityZoneDto dto) {
        return writeSecurityZone(id, dto, false);
    }

    @Override
    public SecurityZoneDto replaceSecurityZone(UUID id, SecurityZoneDto dto) {
        return writeSecurityZone(id, dto, true);
    }

    /** PATCH ({@code replace} false: null leaves a field unchanged) or PUT (null clears it). */
    private SecurityZoneDto writeSecurityZone(UUID id, SecurityZoneDto dto, boolean replace) {
        Organisation org = requireTenantOrg();
        SecurityZone zone = securityZoneRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Security zone not found"));
        applyZoneFields(zone, dto, replace);
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

    private void applyZoneFields(SecurityZone z, SecurityZoneDto dto, boolean replace) {
        if (dto.getName() != null) z.setName(dto.getName());
        if (dto.getPurdueLevel() != null) z.setPurdueLevel(dto.getPurdueLevel());
        put(replace, dto.getDescription(), z::setDescription);
        put(replace, dto.getAllowedProtocols(), z::setAllowedProtocols);
        // assetCount is derived from the ICS assets in the zone (toZoneDto); client input is ignored.
        put(replace, dto.getNetworkRange(), z::setNetworkRange);
    }

    private SecurityZoneDto toZoneDto(SecurityZone z) {
        SecurityZoneDto dto = new SecurityZoneDto();
        dto.setId(z.getId());
        dto.setOrganisationId(z.getOrganisation().getId());
        dto.setName(z.getName());
        dto.setPurdueLevel(z.getPurdueLevel());
        dto.setDescription(z.getDescription());
        dto.setAllowedProtocols(z.getAllowedProtocols());
        // Derived: the live ICS assets placed in this zone (the stored column is no longer written).
        dto.setAssetCount(z.getId() == null ? 0 : (int) icsAssetRepository.countBySecurityZoneAndDeletedAtIsNull(z));
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
            throw new DuplicateFieldException("assetId", "ICS metadata already exists for this asset");
        }
        IcsAsset icsAsset = new IcsAsset();
        icsAsset.setOrganisation(org);
        icsAsset.setAsset(resolveAsset(dto.getAssetId()));
        applyIcsAssetFields(icsAsset, dto, false);
        return toIcsAssetDto(icsAssetRepository.save(icsAsset));
    }

    @Override
    public IcsAssetDto updateIcsAsset(UUID id, IcsAssetDto dto) {
        return writeIcsAsset(id, dto, false);
    }

    @Override
    public IcsAssetDto replaceIcsAsset(UUID id, IcsAssetDto dto) {
        return writeIcsAsset(id, dto, true);
    }

    /** PATCH ({@code replace} false: null leaves a field unchanged) or PUT (null clears it). */
    private IcsAssetDto writeIcsAsset(UUID id, IcsAssetDto dto, boolean replace) {
        Organisation org = requireTenantOrg();
        IcsAsset icsAsset = icsAssetRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("ICS asset not found"));
        applyIcsAssetFields(icsAsset, dto, replace);
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

    private void applyIcsAssetFields(IcsAsset a, IcsAssetDto dto, boolean replace) {
        // The linked asset is fixed at create: assetId is ignored here.
        if (dto.getSecurityZoneId() != null) {
            SecurityZone zone = securityZoneRepository.findByIdAndOrganisationAndDeletedAtIsNull(
                    dto.getSecurityZoneId(), a.getOrganisation())
                    .orElseThrow(() -> new FieldValidationException("securityZoneId", "Security zone not found"));
            a.setSecurityZone(zone);
        } else if (replace) {
            a.setSecurityZone(null);
        }
        put(replace, dto.getFirmwareVersion(), a::setFirmwareVersion);
        put(replace, dto.getProtocol(), a::setProtocol);
        putOr(replace, dto.getVendorSupportStatus(), IcsAsset.VendorSupportStatus.UNKNOWN, a::setVendorSupportStatus);
        put(replace, dto.getLastPatchedAt(), a::setLastPatchedAt);
        put(replace, dto.getKnownVulnerabilities(), a::setKnownVulnerabilities);
        putOr(replace, dto.getIsolated(), Boolean.FALSE, a::setIsolated);
        put(replace, dto.getNotes(), a::setNotes);
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
        applyPatchRecordFields(record, dto, false);
        return toPatchRecordDto(patchRecordRepository.save(record));
    }

    @Override
    public PatchRecordDto updatePatchRecord(UUID id, PatchRecordDto dto) {
        return writePatchRecord(id, dto, false);
    }

    @Override
    public PatchRecordDto replacePatchRecord(UUID id, PatchRecordDto dto) {
        return writePatchRecord(id, dto, true);
    }

    /** PATCH ({@code replace} false: null leaves a field unchanged) or PUT (null clears it). */
    private PatchRecordDto writePatchRecord(UUID id, PatchRecordDto dto, boolean replace) {
        Organisation org = requireTenantOrg();
        PatchRecord record = patchRecordRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Patch record not found"));
        applyPatchRecordFields(record, dto, replace);
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

    private void applyPatchRecordFields(PatchRecord r, PatchRecordDto dto, boolean replace) {
        // The linked asset is fixed at create: assetId is ignored here.
        if (dto.getPatchName() != null) r.setPatchName(dto.getPatchName());
        put(replace, dto.getVersion(), r::setVersion);
        put(replace, dto.getAppliedAt(), r::setAppliedAt);
        putOr(replace, dto.getTestEnvironmentValidated(), Boolean.FALSE, r::setTestEnvironmentValidated);
        put(replace, dto.getRollbackPlan(), r::setRollbackPlan);
        // Who applied the patch is the signed-in user who recorded it as applied,
        // never client input.
        if (dto.getStatus() != null) {
            if (dto.getStatus() == PatchRecord.PatchStatus.APPLIED
                    && (r.getStatus() != PatchRecord.PatchStatus.APPLIED || r.getAppliedByEmail() == null)) {
                r.setAppliedByEmail(currentUserEmail());
            }
            r.setStatus(dto.getStatus());
        }
        put(replace, dto.getNotes(), r::setNotes);
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
        assertRequirementNumberFree(org, dto.getRequirementNumber(), record.getId());
        applyPciSaqFields(record, dto, false);
        checkCompensatingControl(record);
        return toPciSaqDto(pciSaqRecordRepository.save(record));
    }

    @Override
    public PciSaqRecordDto updatePciSaqRecord(UUID id, PciSaqRecordDto dto) {
        return writePciSaqRecord(id, dto, false);
    }

    @Override
    public PciSaqRecordDto replacePciSaqRecord(UUID id, PciSaqRecordDto dto) {
        return writePciSaqRecord(id, dto, true);
    }

    /** PATCH ({@code replace} false: null leaves a field unchanged) or PUT (null clears it). */
    private PciSaqRecordDto writePciSaqRecord(UUID id, PciSaqRecordDto dto, boolean replace) {
        Organisation org = requireTenantOrg();
        PciSaqRecord record = pciSaqRecordRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("PCI SAQ record not found"));
        assertRequirementNumberFree(org, dto.getRequirementNumber(), record.getId());
        applyPciSaqFields(record, dto, replace);
        checkCompensatingControl(record);
        return toPciSaqDto(pciSaqRecordRepository.save(record));
    }

    /** A PCI requirement is recorded once per organisation (V46 uq_pci_saq_org_requirement_live). */
    private void assertRequirementNumberFree(Organisation org, String requirementNumber, UUID selfId) {
        if (requirementNumber == null) return;
        pciSaqRecordRepository.findByOrganisationAndRequirementNumberAndDeletedAtIsNull(org, requirementNumber)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw new DuplicateFieldException("requirementNumber",
                            "Requirement " + requirementNumber + " is already recorded");
                });
    }

    /** An answer of COMPENSATING_CONTROL must say what the compensating control is. */
    private static void checkCompensatingControl(PciSaqRecord r) {
        if (r.getComplianceStatus() == PciSaqRecord.ComplianceAnswer.COMPENSATING_CONTROL
                && (r.getCompensatingControl() == null || r.getCompensatingControl().isBlank())) {
            throw new FieldValidationException("compensatingControl",
                    "describe the compensating control for this requirement");
        }
    }

    private void applyPciSaqFields(PciSaqRecord r, PciSaqRecordDto dto, boolean replace) {
        if (dto.getRequirementNumber() != null) r.setRequirementNumber(dto.getRequirementNumber());
        put(replace, dto.getRequirementText(), r::setRequirementText);
        if (dto.getComplianceStatus() != null) r.setComplianceStatus(dto.getComplianceStatus());
        put(replace, dto.getCompensatingControl(), r::setCompensatingControl);
        put(replace, dto.getEvidenceUrl(), r::setEvidenceUrl);
        put(replace, dto.getTargetDate(), r::setTargetDate);
        put(replace, dto.getNotes(), r::setNotes);
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
        assertPeriodFree(org, dto.getYear(), dto.getMonth(), null);
        SlaMetric metric = new SlaMetric();
        metric.setOrganisation(org);
        applySlaMetricFields(metric, dto, false);
        return toSlaMetricDto(slaMetricRepository.save(metric));
    }

    @Override
    public SlaMetricDto updateSlaMetric(UUID id, SlaMetricDto dto) {
        return writeSlaMetric(id, dto, false);
    }

    @Override
    public SlaMetricDto replaceSlaMetric(UUID id, SlaMetricDto dto) {
        return writeSlaMetric(id, dto, true);
    }

    /** PATCH ({@code replace} false: null leaves a field unchanged) or PUT (null clears it). */
    private SlaMetricDto writeSlaMetric(UUID id, SlaMetricDto dto, boolean replace) {
        Organisation org = requireTenantOrg();
        SlaMetric metric = slaMetricRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("SLA metric not found"));
        assertPeriodFree(org,
                dto.getYear() != null ? dto.getYear() : metric.getYear(),
                dto.getMonth() != null ? dto.getMonth() : metric.getMonth(),
                metric.getId());
        applySlaMetricFields(metric, dto, replace);
        return toSlaMetricDto(slaMetricRepository.save(metric));
    }

    /** One SLA metric per organisation and month (V46 uq_sla_metric_org_period_live). */
    private void assertPeriodFree(Organisation org, Integer year, Integer month, UUID selfId) {
        if (year == null || month == null) return;
        slaMetricRepository.findByOrganisationAndYearAndMonthAndDeletedAtIsNull(org, year, month)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw new DuplicateFieldException("month",
                            "An SLA metric for " + year + "-" + String.format("%02d", month) + " already exists");
                });
    }

    private void applySlaMetricFields(SlaMetric m, SlaMetricDto dto, boolean replace) {
        if (dto.getMonth() != null) m.setMonth(dto.getMonth());
        if (dto.getYear() != null) m.setYear(dto.getYear());
        if (dto.getUptimePercent() != null) m.setUptimePercent(dto.getUptimePercent());
        put(replace, dto.getPlannedDowntimeMinutes(), m::setPlannedDowntimeMinutes);
        put(replace, dto.getUnplannedDowntimeMinutes(), m::setUnplannedDowntimeMinutes);
        put(replace, dto.getIncidentCount(), m::setIncidentCount);
        put(replace, dto.getRtoMinutes(), m::setRtoMinutes);
        put(replace, dto.getRpoMinutes(), m::setRpoMinutes);
        putOr(replace, dto.getSlaBreached(), Boolean.FALSE, m::setSlaBreached);
        put(replace, dto.getNotes(), m::setNotes);
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
        applyVulnScanFields(scan, dto, false);
        return toVulnScanDto(vulnerabilityScanRepository.save(scan));
    }

    @Override
    public VulnerabilityScanDto updateVulnerabilityScan(UUID id, VulnerabilityScanDto dto) {
        return writeVulnerabilityScan(id, dto, false);
    }

    @Override
    public VulnerabilityScanDto replaceVulnerabilityScan(UUID id, VulnerabilityScanDto dto) {
        return writeVulnerabilityScan(id, dto, true);
    }

    /** PATCH ({@code replace} false: null leaves a field unchanged) or PUT (null clears it). */
    private VulnerabilityScanDto writeVulnerabilityScan(UUID id, VulnerabilityScanDto dto, boolean replace) {
        Organisation org = requireTenantOrg();
        VulnerabilityScan scan = vulnerabilityScanRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Vulnerability scan not found"));
        applyVulnScanFields(scan, dto, replace);
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

    private void applyVulnScanFields(VulnerabilityScan s, VulnerabilityScanDto dto, boolean replace) {
        if (dto.getScanDate() != null) s.setScanDate(dto.getScanDate());
        put(replace, dto.getScannerTool(), s::setScannerTool);
        if (dto.getScanType() != null) s.setScanType(dto.getScanType());
        putOr(replace, dto.getCriticalCount(), 0, s::setCriticalCount);
        putOr(replace, dto.getHighCount(), 0, s::setHighCount);
        putOr(replace, dto.getMediumCount(), 0, s::setMediumCount);
        putOr(replace, dto.getLowCount(), 0, s::setLowCount);
        if (dto.getStatus() != null) s.setStatus(dto.getStatus());
        put(replace, dto.getReportUrl(), s::setReportUrl);
        put(replace, dto.getNextScanDue(), s::setNextScanDue);
        put(replace, dto.getNotes(), s::setNotes);
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
        applyFilingFields(filing, dto, false);
        return toFilingDto(regulatoryFilingRepository.save(filing));
    }

    @Override
    public RegulatoryFilingDto updateRegulatoryFiling(UUID id, RegulatoryFilingDto dto) {
        return writeRegulatoryFiling(id, dto, false);
    }

    @Override
    public RegulatoryFilingDto replaceRegulatoryFiling(UUID id, RegulatoryFilingDto dto) {
        return writeRegulatoryFiling(id, dto, true);
    }

    /** PATCH ({@code replace} false: null leaves a field unchanged) or PUT (null clears it). */
    private RegulatoryFilingDto writeRegulatoryFiling(UUID id, RegulatoryFilingDto dto, boolean replace) {
        Organisation org = requireTenantOrg();
        RegulatoryFiling filing = regulatoryFilingRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Regulatory filing not found"));
        applyFilingFields(filing, dto, replace);
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

    private void applyFilingFields(RegulatoryFiling f, RegulatoryFilingDto dto, boolean replace) {
        if (dto.getFilingType() != null) f.setFilingType(dto.getFilingType());
        if (dto.getRegulator() != null) f.setRegulator(dto.getRegulator());
        if (dto.getDueDate() != null) f.setDueDate(dto.getDueDate());
        put(replace, dto.getSubmittedAt(), f::setSubmittedAt);
        put(replace, dto.getReference(), f::setReference);
        if (dto.getStatus() != null) f.setStatus(dto.getStatus());
        put(replace, dto.getNotes(), f::setNotes);
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
