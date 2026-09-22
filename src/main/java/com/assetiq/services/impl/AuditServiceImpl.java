package com.assetiq.services.impl;

import com.assetiq.dto.AssetAuditDto;
import com.assetiq.enums.AuditStatus;
import com.assetiq.models.AssetAudit;
import com.assetiq.models.Organisation;
import com.assetiq.models.Department;
import com.assetiq.models.User;
import com.assetiq.repositories.*;
import com.assetiq.services.AuditService;
import com.assetiq.services.TenantAwareService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AuditServiceImpl extends TenantAwareService implements AuditService {

    private final AssetAuditRepository auditRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    public AuditServiceImpl(AssetAuditRepository auditRepository,
            OrganisationRepository organisationRepository,
            DepartmentRepository departmentRepository,
            UserRepository userRepository) {
        super(organisationRepository);
        this.auditRepository = auditRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
    }

    @Override
    public AssetAuditDto createAudit(AssetAuditDto auditDto) {
        Organisation org = requireTenantOrg();

        // No department = an organisation-wide audit (the web form's default).
        Department department = auditDto.getDepartmentId() == null ? null
                : departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(auditDto.getDepartmentId(), org)
                        .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));

        User conductor = auditDto.getConductedById() != null
                ? userRepository.findByIdAndOrganisation(auditDto.getConductedById(), org)
                        .orElseThrow(() -> new IllegalArgumentException("Conductor not found in your organisation"))
                : currentUser(org);

        AssetAudit audit = new AssetAudit();
        audit.setOrganisation(org);
        audit.setDepartment(department);
        audit.setAuditDate(auditDto.getAuditDate());
        audit.setConductedBy(conductor);
        audit.setStatus(initialStatus(auditDto.getStatus()));
        audit.setRemarks(auditDto.getRemarks());

        return mapToDto(auditRepository.save(audit));
    }

    @Override
    @Transactional(readOnly = true)
    public AssetAuditDto getAuditById(UUID id) {
        Organisation org = requireTenantOrg();
        AssetAudit audit = auditRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Audit not found"));
        return mapToDto(audit);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<AssetAuditDto> getAuditsByOrganisation(UUID organisationId) {
        // Always scope to tenant context, ignore param
        Organisation org = requireTenantOrg();
        return auditRepository.findByOrganisationAndDeletedAtIsNull(org).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<AssetAuditDto> getAuditsByDepartment(UUID departmentId) {
        Organisation org = requireTenantOrg();
        departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(departmentId, org)
                .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));
        return auditRepository.findByDepartmentIdAndDeletedAtIsNull(departmentId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<AssetAuditDto> getAuditsByDateRange(LocalDate startDate, LocalDate endDate) {
        Organisation org = requireTenantOrg();
        return auditRepository.findByOrganisationAndAuditDateBetweenAndDeletedAtIsNull(org, startDate, endDate)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<AssetAuditDto> getAuditsByConductor(UUID userId) {
        Organisation org = requireTenantOrg();
        userRepository.findByIdAndOrganisation(userId, org)
                .orElseThrow(() -> new IllegalArgumentException("User not found in your organisation"));
        return auditRepository.findByConductedByIdAndDeletedAtIsNull(userId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    public AssetAuditDto updateAuditStatus(UUID auditId, String status) {
        Organisation org = requireTenantOrg();
        AssetAudit audit = auditRepository.findByIdAndOrganisationAndDeletedAtIsNull(auditId, org)
                .orElseThrow(() -> new IllegalArgumentException("Audit not found"));

        AuditStatus next;
        try {
            next = AuditStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid audit status: " + status);
        }
        AuditStatus current = audit.getStatus() != null ? audit.getStatus() : AuditStatus.PLANNED;
        if (current != next && !TRANSITIONS.getOrDefault(current, Set.of()).contains(next)) {
            throw new IllegalStateException("An audit cannot move from " + current + " to " + next);
        }
        audit.setStatus(next);

        return mapToDto(auditRepository.save(audit));
    }

    /**
     * Allowed audit status changes. COMPLETED and CANCELLED are final (a completed
     * audit is a compliance record); a discrepancy must be RESOLVED (or the count
     * resumed) before the audit can be completed.
     */
    /** Statuses an audit may be created in; later states are reached through transitions. */
    static final Set<AuditStatus> CREATABLE = EnumSet.of(AuditStatus.PLANNED, AuditStatus.IN_PROGRESS);

    /**
     * A new audit starts PLANNED or IN_PROGRESS. Creating one as COMPLETED or
     * RESOLVED skipped the audit itself.
     */
    static AuditStatus initialStatus(AuditStatus requested) {
        if (requested == null) return AuditStatus.PLANNED;
        if (!CREATABLE.contains(requested)) {
            throw new IllegalArgumentException("A new audit must start as PLANNED or IN_PROGRESS, not " + requested);
        }
        return requested;
    }

    static final Map<AuditStatus, Set<AuditStatus>> TRANSITIONS = Map.of(
            AuditStatus.PLANNED, EnumSet.of(AuditStatus.IN_PROGRESS, AuditStatus.COMPLETED,
                    AuditStatus.DISCREPANCY_FOUND, AuditStatus.CANCELLED),
            AuditStatus.IN_PROGRESS, EnumSet.of(AuditStatus.COMPLETED, AuditStatus.DISCREPANCY_FOUND,
                    AuditStatus.CANCELLED),
            AuditStatus.DISCREPANCY_FOUND, EnumSet.of(AuditStatus.RESOLVED, AuditStatus.IN_PROGRESS),
            AuditStatus.RESOLVED, EnumSet.of(AuditStatus.COMPLETED),
            AuditStatus.COMPLETED, EnumSet.noneOf(AuditStatus.class),
            AuditStatus.CANCELLED, EnumSet.noneOf(AuditStatus.class));

    private User currentUser(Organisation org) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new AccessDeniedException("No authenticated user in security context");
        }
        return userRepository.findByEmailAndOrganisationId(auth.getName(), org.getId())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user not found in organisation"));
    }

    @Override
    public void deleteAudit(UUID id) {
        // Audit records should not be deleted — they are immutable compliance records
        throw new IllegalStateException("Audit records cannot be deleted. They are immutable compliance records.");
    }

    private AssetAuditDto mapToDto(AssetAudit audit) {
        AssetAuditDto dto = new AssetAuditDto();
        dto.setId(audit.getId());
        dto.setOrganisationId(audit.getOrganisation().getId());
        if (audit.getDepartment() != null) {
            dto.setDepartmentId(audit.getDepartment().getId());
        }
        dto.setAuditDate(audit.getAuditDate());
        dto.setConductedById(audit.getConductedBy().getId());
        // Legacy rows can have no status (backfilled by V47); read them as PLANNED.
        dto.setStatus(audit.getStatus() != null ? audit.getStatus() : AuditStatus.PLANNED);
        dto.setRemarks(audit.getRemarks());
        return dto;
    }
}
