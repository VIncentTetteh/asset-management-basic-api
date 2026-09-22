package com.assetiq.services.impl;

import com.assetiq.assets.AssetQrCodes;
import com.assetiq.dto.AssetAuditDto;
import com.assetiq.dto.AuditItemDiscrepancyRequest;
import com.assetiq.dto.AuditItemDto;
import com.assetiq.dto.AuditItemVerifyRequest;
import com.assetiq.dto.PagedResponseDto;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.AuditDiscrepancyType;
import com.assetiq.enums.AuditItemStatus;
import com.assetiq.enums.AuditStatus;
import com.assetiq.enums.UserStatus;
import com.assetiq.models.Asset;
import com.assetiq.models.AssetAudit;
import com.assetiq.models.AuditItem;
import com.assetiq.models.Organisation;
import com.assetiq.models.Department;
import com.assetiq.models.User;
import com.assetiq.repositories.*;
import com.assetiq.services.AuditService;
import com.assetiq.services.UserDisplayNames;
import com.assetiq.services.TenantAwareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AuditServiceImpl extends TenantAwareService implements AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditServiceImpl.class);

    /** Page size when the caller asks for none, and the ceiling when they ask for too much. */
    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 200;

    private final AssetAuditRepository auditRepository;
    private final AuditItemRepository auditItemRepository;
    private final AssetRepository assetRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    public AuditServiceImpl(AssetAuditRepository auditRepository,
            AuditItemRepository auditItemRepository,
            AssetRepository assetRepository,
            OrganisationRepository organisationRepository,
            DepartmentRepository departmentRepository,
            UserRepository userRepository) {
        super(organisationRepository);
        this.auditRepository = auditRepository;
        this.auditItemRepository = auditItemRepository;
        this.assetRepository = assetRepository;
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
                ? requireActiveConductor(auditDto.getConductedById(), org)
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
        return new java.util.LinkedHashSet<>(mapAll(auditRepository.findByOrganisationAndDeletedAtIsNull(org), org));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<AssetAuditDto> getAuditsByDepartment(UUID departmentId) {
        Organisation org = requireTenantOrg();
        departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(departmentId, org)
                .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));
        return new java.util.LinkedHashSet<>(mapAll(
                auditRepository.findByDepartmentIdAndDeletedAtIsNull(departmentId), org));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<AssetAuditDto> getAuditsByDateRange(LocalDate startDate, LocalDate endDate) {
        Organisation org = requireTenantOrg();
        return new java.util.LinkedHashSet<>(mapAll(
                auditRepository.findByOrganisationAndAuditDateBetweenAndDeletedAtIsNull(org, startDate, endDate),
                org));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<AssetAuditDto> getAuditsByConductor(UUID userId) {
        Organisation org = requireTenantOrg();
        userRepository.findByIdAndOrganisation(userId, org)
                .orElseThrow(() -> new IllegalArgumentException("User not found in your organisation"));
        return new java.util.LinkedHashSet<>(mapAll(
                auditRepository.findByConductedByIdAndDeletedAtIsNull(userId), org));
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
        AssetAudit saved = auditRepository.save(audit);
        // Starting an audit is what creates its count sheet: an IN_PROGRESS audit
        // with nothing to count is the state the old "Verified" seal was reading.
        if (next == AuditStatus.IN_PROGRESS) {
            generateItemsFor(saved, org);
        }
        return mapToDto(saved);
    }

    @Override
    public AssetAuditDto updateAuditRemarks(UUID auditId, String remarks) {
        Organisation org = requireTenantOrg();
        AssetAudit audit = auditRepository.findByIdAndOrganisationAndDeletedAtIsNull(auditId, org)
                .orElseThrow(() -> new IllegalArgumentException("Audit not found"));
        AuditStatus current = audit.getStatus() != null ? audit.getStatus() : AuditStatus.PLANNED;
        if (current == AuditStatus.COMPLETED || current == AuditStatus.CANCELLED) {
            throw new IllegalStateException("A " + current + " audit is a final record; its remarks cannot change");
        }
        audit.setRemarks(remarks == null || remarks.isBlank() ? null : remarks.trim());
        return mapToDto(auditRepository.save(audit));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<AssetAuditDto> searchAudits(UUID departmentId, LocalDate startDate, LocalDate endDate,
                                                      UUID conductedById, AuditStatus status) {
        Organisation org = requireTenantOrg();
        return auditRepository.findByOrganisationAndDeletedAtIsNull(org).stream()
                .filter(a -> departmentId == null
                        || (a.getDepartment() != null && departmentId.equals(a.getDepartment().getId())))
                .filter(a -> startDate == null || !a.getAuditDate().isBefore(startDate))
                .filter(a -> endDate == null || !a.getAuditDate().isAfter(endDate))
                .filter(a -> conductedById == null || conductedById.equals(a.getConductedBy().getId()))
                .filter(a -> status == null || effectiveStatus(a) == status)
                .sorted(java.util.Comparator.comparing(AssetAudit::getAuditDate,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .collect(Collectors.collectingAndThen(Collectors.toList(), audits -> mapAll(audits, org)));
    }

    /** Legacy rows can have no status (backfilled by V47); they read as PLANNED. */
    private static AuditStatus effectiveStatus(AssetAudit audit) {
        return audit.getStatus() != null ? audit.getStatus() : AuditStatus.PLANNED;
    }

    /** An explicitly named auditor must be a live, active account in the organisation. */
    private User requireActiveConductor(UUID userId, Organisation org) {
        User user = userRepository.findByIdAndOrganisation(userId, org)
                .orElseThrow(() -> new IllegalArgumentException("Conductor not found in your organisation"));
        if (user.getDeletedAt() != null || user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("The auditor must be an active user");
        }
        return user;
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
        Organisation org = audit.getOrganisation();
        return mapToDto(audit, progressOf(org, List.of(audit.getId())));
    }

    /** Maps a batch of audits with their progress counted in a single query. */
    private List<AssetAuditDto> mapAll(Collection<AssetAudit> audits, Organisation org) {
        if (audits.isEmpty()) {
            return List.of();
        }
        Map<UUID, Progress> progress = progressOf(org, audits.stream().map(AssetAudit::getId).toList());
        return audits.stream().map(a -> mapToDto(a, progress)).toList();
    }

    private AssetAuditDto mapToDto(AssetAudit audit, Map<UUID, Progress> progress) {
        AssetAuditDto dto = new AssetAuditDto();
        dto.setId(audit.getId());
        dto.setOrganisationId(audit.getOrganisation().getId());
        if (audit.getDepartment() != null) {
            dto.setDepartmentId(audit.getDepartment().getId());
            dto.setDepartmentName(audit.getDepartment().getName());
        }
        dto.setAuditDate(audit.getAuditDate());
        dto.setConductedById(audit.getConductedBy().getId());
        dto.setConductedByName(UserDisplayNames.of(audit.getConductedBy()));
        dto.setStatus(effectiveStatus(audit));
        dto.setRemarks(audit.getRemarks());

        Progress counts = progress.getOrDefault(audit.getId(), Progress.EMPTY);
        dto.setTotalItemCount(counts.total());
        dto.setVerifiedItemCount(counts.verified());
        dto.setDiscrepancyCount(counts.discrepancies());
        // An audit with no sheet is not a verified audit, however few items it has.
        dto.setAllItemsVerified(counts.total() > 0 && counts.verified() == counts.total());
        return dto;
    }

    /** Count-sheet totals for one audit. */
    private record Progress(long total, long verified, long discrepancies) {
        static final Progress EMPTY = new Progress(0, 0, 0);
    }

    private Map<UUID, Progress> progressOf(Organisation org, Collection<UUID> auditIds) {
        if (auditIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, long[]> tallies = new HashMap<>();
        for (Object[] row : auditItemRepository.countByAuditAndStatus(org, auditIds)) {
            UUID auditId = (UUID) row[0];
            AuditItemStatus status = (AuditItemStatus) row[1];
            long count = ((Number) row[2]).longValue();
            long[] tally = tallies.computeIfAbsent(auditId, k -> new long[3]);
            tally[0] += count;
            if (status == AuditItemStatus.VERIFIED) tally[1] += count;
            if (status == AuditItemStatus.DISCREPANCY) tally[2] += count;
        }
        Map<UUID, Progress> progress = new HashMap<>();
        tallies.forEach((auditId, t) -> progress.put(auditId, new Progress(t[0], t[1], t[2])));
        return progress;
    }

    // ── Count sheet ──────────────────────────────────────────────────────────

    @Override
    public AssetAuditDto generateAuditItems(UUID auditId) {
        Organisation org = requireTenantOrg();
        AssetAudit audit = requireOpenAudit(auditId, org);
        int created = generateItemsFor(audit, org);
        logger.info("[AUDIT] Generated {} count-sheet item(s) for audit {}", created, auditId);
        return mapToDto(audit);
    }

    /**
     * Tops up the audit's count sheet with every in-scope asset that is not on it
     * yet, and returns how many were added.
     *
     * <p>Scope is the audit's department, or the whole organisation when it has
     * none. Disposed assets are left out: they are not meant to be there, so their
     * absence is not a finding.
     */
    private int generateItemsFor(AssetAudit audit, Organisation org) {
        Set<UUID> already = auditItemRepository
                .findByAuditIdAndOrganisationAndDeletedAtIsNull(audit.getId(), org)
                .stream().map(item -> item.getAsset().getId()).collect(Collectors.toCollection(HashSet::new));

        UUID departmentId = audit.getDepartment() == null ? null : audit.getDepartment().getId();
        List<AuditItem> fresh = new ArrayList<>();
        for (Asset asset : assetRepository.findAllByOrganisationAndDeletedAtIsNull(org)) {
            if (asset.getStatus() == AssetStatus.DISPOSED) continue;
            if (departmentId != null && (asset.getDepartment() == null
                    || !departmentId.equals(asset.getDepartment().getId()))) {
                continue;
            }
            if (!already.add(asset.getId())) continue;
            fresh.add(newItem(audit, org, asset));
        }
        auditItemRepository.saveAll(fresh);
        return fresh.size();
    }

    private static AuditItem newItem(AssetAudit audit, Organisation org, Asset asset) {
        AuditItem item = new AuditItem();
        item.setOrganisation(org);
        item.setAudit(audit);
        item.setAsset(asset);
        item.setStatus(AuditItemStatus.PENDING);
        item.setDiscrepancyFlag(false);
        item.setExpectedLocation(asset.getLocation() == null ? null : asset.getLocation().getName());
        return item;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponseDto<AuditItemDto> listAuditItems(UUID auditId, AuditItemStatus status,
                                                         AuditDiscrepancyType discrepancyType, String search,
                                                         Integer page, Integer size) {
        Organisation org = requireTenantOrg();
        requireAudit(auditId, org);

        int pageNumber = page != null && page >= 0 ? page : 0;
        int pageSize = size != null && size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        String pattern = search == null || search.isBlank()
                ? "%" : "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";

        Page<AuditItem> found = auditItemRepository.search(auditId, org, status, discrepancyType, pattern,
                PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.ASC, "asset.assetTag")));

        PagedResponseDto<AuditItemDto> response = new PagedResponseDto<>();
        response.setTotal(found.getTotalElements());
        response.setLimit(pageSize);
        response.setOffset((long) pageNumber * pageSize);
        response.setItems(found.getContent().stream().map(AuditServiceImpl::mapItemToDto).toList());
        return response;
    }

    @Override
    public AuditItemDto verifyAuditItem(UUID auditId, AuditItemVerifyRequest request) {
        Organisation org = requireTenantOrg();
        AssetAudit audit = requireOpenAudit(auditId, org);
        Asset asset = resolveScannedAsset(request.scan(), org);

        Optional<AuditItem> onSheet = auditItemRepository
                .findByAuditIdAndAssetIdAndOrganisationAndDeletedAtIsNull(auditId, asset.getId(), org);

        AuditItem item = onSheet.orElseGet(() -> newItem(audit, org, asset));
        item.setActualLocation(blankToNull(request.actualLocation()));
        item.setCondition(blankToNull(request.condition()));
        if (blankToNull(request.remarks()) != null) {
            item.setRemarks(request.remarks().trim());
        }
        item.setVerifiedAt(Instant.now());
        item.setVerifiedBy(currentUser(org));

        if (onSheet.isEmpty()) {
            // Scanned an asset the sheet does not have: that is the UNEXPECTED
            // finding, not a verification, and pretending otherwise would let an
            // out-of-scope asset close an audit.
            item.setStatus(AuditItemStatus.DISCREPANCY);
            item.setDiscrepancyFlag(true);
            item.setDiscrepancyType(AuditDiscrepancyType.UNEXPECTED);
            item.setDiscrepancyReason("Scanned during the audit but not in its scope when the sheet was generated");
            flagAuditOpenDiscrepancy(audit);
        } else {
            item.setStatus(AuditItemStatus.VERIFIED);
            item.setDiscrepancyFlag(false);
            item.setDiscrepancyType(null);
            item.setDiscrepancyReason(null);
        }

        AuditItem saved = auditItemRepository.save(item);
        logger.info("[AUDIT] Item {} of audit {} recorded as {}", saved.getId(), auditId, saved.getStatus());
        return mapItemToDto(saved);
    }

    @Override
    public AuditItemDto flagAuditItemDiscrepancy(UUID auditId, UUID itemId, AuditItemDiscrepancyRequest request) {
        Organisation org = requireTenantOrg();
        AssetAudit audit = requireOpenAudit(auditId, org);
        AuditItem item = auditItemRepository
                .findByIdAndAuditIdAndOrganisationAndDeletedAtIsNull(itemId, auditId, org)
                .orElseThrow(() -> new IllegalArgumentException("Audit item not found"));

        item.setStatus(AuditItemStatus.DISCREPANCY);
        item.setDiscrepancyFlag(true);
        item.setDiscrepancyType(request.discrepancyType());
        item.setDiscrepancyReason(request.reason().trim());
        if (blankToNull(request.actualLocation()) != null) {
            item.setActualLocation(request.actualLocation().trim());
        }
        item.setVerifiedAt(Instant.now());
        item.setVerifiedBy(currentUser(org));
        flagAuditOpenDiscrepancy(audit);

        AuditItem saved = auditItemRepository.save(item);
        logger.info("[AUDIT] Item {} of audit {} flagged {}", saved.getId(), auditId, request.discrepancyType());
        return mapItemToDto(saved);
    }

    /**
     * Moves an open audit to DISCREPANCY_FOUND, using the same transition table the
     * status endpoint enforces. A finding that leaves the audit looking clean is
     * worse than no finding at all.
     */
    private void flagAuditOpenDiscrepancy(AssetAudit audit) {
        AuditStatus current = effectiveStatus(audit);
        if (TRANSITIONS.getOrDefault(current, Set.of()).contains(AuditStatus.DISCREPANCY_FOUND)) {
            audit.setStatus(AuditStatus.DISCREPANCY_FOUND);
            auditRepository.save(audit);
        }
    }

    /**
     * Reads a scanned value as an asset: a QR scan link, the legacy
     * {@code asset:<uuid>} text or a bare id go through
     * {@link AssetQrCodes#parse}, and anything else is tried as an asset tag so the
     * same endpoint serves the camera and the keyboard.
     */
    private Asset resolveScannedAsset(String scanned, Organisation org) {
        String value = scanned == null ? "" : scanned.trim();
        Optional<Asset> byId = AssetQrCodes.parse(value)
                .flatMap(id -> assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org));
        return byId.or(() -> assetRepository
                        .findByAssetTagIgnoreCaseAndOrganisationAndDeletedAtIsNull(value, org))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No asset in your organisation matches that code or tag"));
    }

    private AssetAudit requireAudit(UUID auditId, Organisation org) {
        return auditRepository.findByIdAndOrganisationAndDeletedAtIsNull(auditId, org)
                .orElseThrow(() -> new IllegalArgumentException("Audit not found"));
    }

    /** A COMPLETED or CANCELLED audit is a closed compliance record; its sheet is frozen. */
    private AssetAudit requireOpenAudit(UUID auditId, Organisation org) {
        AssetAudit audit = requireAudit(auditId, org);
        AuditStatus current = effectiveStatus(audit);
        if (current == AuditStatus.COMPLETED || current == AuditStatus.CANCELLED) {
            throw new IllegalStateException("A " + current + " audit is a final record; its items cannot change");
        }
        return audit;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static AuditItemDto mapItemToDto(AuditItem item) {
        AuditItemDto dto = new AuditItemDto();
        dto.setId(item.getId());
        dto.setAuditId(item.getAudit().getId());
        dto.setAssetId(item.getAsset().getId());
        dto.setAssetTag(item.getAsset().getAssetTag());
        dto.setAssetName(item.getAsset().getName());
        dto.setStatus(item.getStatus());
        dto.setExpectedLocation(item.getExpectedLocation());
        dto.setActualLocation(item.getActualLocation());
        dto.setCondition(item.getCondition());
        dto.setDiscrepancyFlag(item.getDiscrepancyFlag());
        dto.setDiscrepancyType(item.getDiscrepancyType());
        dto.setDiscrepancyReason(item.getDiscrepancyReason());
        dto.setRemarks(item.getRemarks());
        dto.setVerifiedAt(item.getVerifiedAt());
        dto.setVerifiedByName(UserDisplayNames.of(item.getVerifiedBy()));
        dto.setCreatedAt(item.getCreatedAt());
        dto.setUpdatedAt(item.getUpdatedAt());
        return dto;
    }
}
