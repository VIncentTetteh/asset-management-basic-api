package com.assetiq.services.impl;

import com.assetiq.dto.DisposalRecordDto;
import com.assetiq.models.DisposalRecord;
import com.assetiq.models.Asset;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.DisposalMethod;
import com.assetiq.enums.DisposalStatus;
import com.assetiq.enums.UserStatus;
import com.assetiq.repositories.*;
import com.assetiq.enums.NotificationType;
import com.assetiq.services.CurrencyResolver;
import com.assetiq.services.DisposalService;
import com.assetiq.services.UserDisplayNames;
import com.assetiq.services.NotificationService;
import com.assetiq.services.TenantAwareService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class DisposalServiceImpl extends TenantAwareService implements DisposalService {

    private final DisposalRecordRepository disposalRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public DisposalServiceImpl(DisposalRecordRepository disposalRepository,
            AssetRepository assetRepository,
            OrganisationRepository organisationRepository,
            UserRepository userRepository,
            NotificationService notificationService) {
        super(organisationRepository);
        this.disposalRepository = disposalRepository;
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /**
     * Maker step: records a disposal request. The asset is not touched until a
     * different user approves it (see {@link #approveDisposal}).
     */
    @Override
    public DisposalRecordDto createDisposalRecord(DisposalRecordDto recordDto) {
        Organisation org = requireTenantOrg();

        // Asset must belong to the tenant org
        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(recordDto.getAssetId(), org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found in your organisation"));

        // The requesting identity is authoritative server-side. Never trust a
        // client-supplied user or organisation identifier for a disposal action.
        User requester = resolveCurrentUser(org);

        if (asset.getStatus() == AssetStatus.DISPOSED) {
            throw new IllegalStateException("Asset has already been disposed");
        }
        boolean pending = disposalRepository.findByAssetIdAndDeletedAtIsNull(asset.getId()).stream()
                .anyMatch(d -> d.getStatus() == DisposalStatus.PENDING_APPROVAL);
        if (pending) {
            throw new IllegalStateException("Asset '" + asset.getName()
                    + "' already has a disposal awaiting approval");
        }

        DisposalRecord record = new DisposalRecord();
        record.setAsset(asset);
        record.setDisposalMethod(recordDto.getDisposalMethod());
        record.setDisposalDate(recordDto.getDisposalDate());
        record.setSaleValue(recordDto.getSaleValue());
        record.setCurrency(MaintenanceServiceImpl.recordCurrency(recordDto.getCurrency(), asset));
        record.setStatus(DisposalStatus.PENDING_APPROVAL);
        record.setRequestedBy(requester);
        record.setReason(recordDto.getReason());
        record.setComplianceDocumentUrl(recordDto.getComplianceDocumentUrl());
        record.setOrganisation(org);

        DisposalRecord savedRecord = disposalRepository.save(record);
        notificationService.notifyOrgAdmins(org, NotificationType.DISPOSAL,
                "Asset Disposal Requested",
                "Disposal of asset '" + asset.getName() + "' via " + record.getDisposalMethod()
                        + " is awaiting approval.",
                savedRecord.getId(), "/disposals");
        return mapToDto(savedRecord);
    }

    @Override
    public DisposalRecordDto approveDisposal(UUID id) {
        Organisation org = requireTenantOrg();
        DisposalRecord record = disposalRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Disposal record not found"));
        if (record.getStatus() != DisposalStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Only a pending disposal can be approved (this one is "
                    + record.getStatus() + ")");
        }
        User approver = resolveCurrentUser(org);
        if (record.getRequestedBy() != null && record.getRequestedBy().getId().equals(approver.getId())) {
            throw new AccessDeniedException("A disposal must be approved by someone other than the requester");
        }
        Asset asset = record.getAsset();
        if (asset.getStatus() == AssetStatus.DISPOSED) {
            throw new IllegalStateException("Asset has already been disposed");
        }

        record.setStatus(DisposalStatus.APPROVED);
        record.setApprovedBy(approver);
        record.setApprovedAt(Instant.now());

        // Mark asset as disposed and release it from any assigned user
        asset.setStatus(AssetStatus.DISPOSED);
        asset.setAssignedUser(null);
        assetRepository.save(asset);

        DisposalRecord saved = disposalRepository.save(record);
        notificationService.notifyOrgAdmins(org, NotificationType.DISPOSAL,
                "Asset Disposed",
                "Asset '" + asset.getName() + "' has been disposed via " + record.getDisposalMethod() + ".",
                saved.getId(), "/disposals");
        return mapToDto(saved);
    }

    @Override
    public DisposalRecordDto rejectDisposal(UUID id, String reason) {
        Organisation org = requireTenantOrg();
        DisposalRecord record = disposalRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Disposal record not found"));
        if (record.getStatus() != DisposalStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Only a pending disposal can be rejected (this one is "
                    + record.getStatus() + ")");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A rejection reason is required");
        }
        record.setStatus(DisposalStatus.REJECTED);
        record.setRejectedBy(resolveCurrentUser(org));
        record.setRejectedAt(Instant.now());
        record.setRejectionReason(reason.trim());
        return mapToDto(disposalRepository.save(record));
    }

    @Override
    @Transactional(readOnly = true)
    public DisposalRecordDto getDisposalById(UUID id) {
        Organisation org = requireTenantOrg();
        DisposalRecord record = disposalRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Disposal record not found"));
        return mapToDto(record);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<DisposalRecordDto> getDisposalsByAsset(UUID assetId) {
        Organisation org = requireTenantOrg();
        assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found in your organisation"));
        return disposalRepository.findByAssetIdAndDeletedAtIsNull(assetId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<DisposalRecordDto> getDisposalsByOrganisation(UUID organisationId) {
        Organisation org = requireTenantOrg();
        // Always use tenant org, ignore the passed organisationId
        return disposalRepository.findByOrganisationAndDeletedAtIsNull(org).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<DisposalRecordDto> searchDisposals(UUID assetId, LocalDate startDate, LocalDate endDate,
                                                             UUID approvedById, DisposalStatus status) {
        Organisation org = requireTenantOrg();
        return disposalRepository.findByOrganisationAndDeletedAtIsNull(org).stream()
                .filter(r -> assetId == null || assetId.equals(r.getAsset().getId()))
                .filter(r -> startDate == null || !r.getDisposalDate().isBefore(startDate))
                .filter(r -> endDate == null || !r.getDisposalDate().isAfter(endDate))
                .filter(r -> approvedById == null
                        || (r.getApprovedBy() != null && approvedById.equals(r.getApprovedBy().getId())))
                .filter(r -> status == null || effectiveStatus(r) == status)
                .sorted(java.util.Comparator.comparing(DisposalRecord::getDisposalDate,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .map(this::mapToDto)
                .toList();
    }

    /** Rows from before V44 have no status; they were all effective disposals. */
    private static DisposalStatus effectiveStatus(DisposalRecord r) {
        return r.getStatus() != null ? r.getStatus() : DisposalStatus.APPROVED;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<DisposalRecordDto> getDisposalsByDateRange(LocalDate startDate, LocalDate endDate) {
        Organisation org = requireTenantOrg();
        return disposalRepository.findByOrganisationAndDisposalDateBetweenAndDeletedAtIsNull(org, startDate, endDate)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<DisposalRecordDto> getDisposalsByApprover(UUID userId) {
        Organisation org = requireTenantOrg();
        userRepository.findByIdAndOrganisation(userId, org)
                .orElseThrow(() -> new IllegalArgumentException("User not found in your organisation"));
        return disposalRepository.findByApprovedByIdAndDeletedAtIsNull(userId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    public DisposalRecordDto updateDisposalRecord(UUID id, DisposalRecordDto recordDto) {
        Organisation org = requireTenantOrg();
        DisposalRecord record = disposalRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Disposal record not found"));
        requireEditable(record);

        String currency = MaintenanceServiceImpl.recordCurrency(recordDto.getCurrency(), record.getAsset());
        guardLockedTerms(record, recordDto.getDisposalMethod(), recordDto.getDisposalDate(),
                recordDto.getSaleValue(), currency);
        record.setDisposalMethod(recordDto.getDisposalMethod());
        record.setDisposalDate(recordDto.getDisposalDate());
        record.setSaleValue(recordDto.getSaleValue());
        record.setCurrency(currency);
        record.setReason(recordDto.getReason());
        record.setComplianceDocumentUrl(recordDto.getComplianceDocumentUrl());

        DisposalRecord updatedRecord = disposalRepository.save(record);
        return mapToDto(updatedRecord);
    }

    @Override
    public DisposalRecordDto patchDisposalRecord(UUID id, DisposalRecordDto recordDto) {
        Organisation org = requireTenantOrg();
        DisposalRecord record = disposalRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Disposal record not found"));
        requireEditable(record);

        DisposalMethod method = recordDto.getDisposalMethod() != null
                ? recordDto.getDisposalMethod() : record.getDisposalMethod();
        LocalDate date = recordDto.getDisposalDate() != null ? recordDto.getDisposalDate() : record.getDisposalDate();
        BigDecimal saleValue = recordDto.getSaleValue() != null ? recordDto.getSaleValue() : record.getSaleValue();
        String currency = recordDto.getCurrency() != null
                ? CurrencyResolver.normaliseIsoCode(recordDto.getCurrency()) : record.getCurrency();
        guardLockedTerms(record, method, date, saleValue, currency);

        record.setDisposalMethod(method);
        record.setDisposalDate(date);
        record.setSaleValue(saleValue);
        record.setCurrency(currency);
        if (recordDto.getReason() != null) {
            record.setReason(recordDto.getReason());
        }
        if (recordDto.getComplianceDocumentUrl() != null) {
            record.setComplianceDocumentUrl(recordDto.getComplianceDocumentUrl());
        }

        DisposalRecord updatedRecord = disposalRepository.save(record);
        return mapToDto(updatedRecord);
    }

    /** A rejected disposal is closed; only pending or approved ones can be edited. */
    private static void requireEditable(DisposalRecord record) {
        if (record.getStatus() == DisposalStatus.REJECTED) {
            throw new IllegalStateException("A rejected disposal cannot be edited; request a new one");
        }
    }

    /**
     * Once approved, the financial terms (method, date, sale value, currency) are
     * what the approver signed off, so they are locked. Reason and compliance
     * document stay editable (e.g. attaching the destruction certificate later).
     */
    static void guardLockedTerms(DisposalRecord record, DisposalMethod method, LocalDate date,
                                 BigDecimal saleValue, String currency) {
        if (!record.isEffective()) return;
        boolean changed = !Objects.equals(record.getDisposalMethod(), method)
                || !Objects.equals(record.getDisposalDate(), date)
                || !sameAmount(record.getSaleValue(), saleValue)
                || !Objects.equals(record.effectiveCurrency(), currency != null ? currency : record.effectiveCurrency());
        if (changed) {
            throw new IllegalStateException(
                    "An approved disposal's method, date, sale value and currency are locked");
        }
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return a == b;
        return a.compareTo(b) == 0;
    }

    @Override
    public void deleteDisposalRecord(UUID id) {
        Organisation org = requireTenantOrg();
        DisposalRecord record = disposalRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Disposal record not found"));
        if (record.isEffective()) {
            // The approved record is the asset's disposal evidence; deleting it
            // would leave a DISPOSED asset with no trail.
            throw new IllegalStateException("An approved disposal cannot be deleted");
        }
        record.setDeletedAt(Instant.now());
        disposalRepository.save(record);
    }

    private DisposalRecordDto mapToDto(DisposalRecord record) {
        DisposalRecordDto dto = new DisposalRecordDto();
        dto.setId(record.getId());
        dto.setAssetId(record.getAsset().getId());
        dto.setAssetName(record.getAsset().getName());
        dto.setAssetTag(record.getAsset().getAssetTag());
        dto.setDisposalMethod(record.getDisposalMethod());
        dto.setDisposalDate(record.getDisposalDate());
        dto.setSaleValue(record.getSaleValue());
        dto.setCurrency(record.effectiveCurrency());
        dto.setStatus(effectiveStatus(record));
        if (record.getRequestedBy() != null) {
            dto.setRequestedById(record.getRequestedBy().getId());
            dto.setRequestedByName(UserDisplayNames.of(record.getRequestedBy()));
        }
        if (record.getApprovedBy() != null) {
            dto.setApprovedById(record.getApprovedBy().getId());
            dto.setApprovedByName(UserDisplayNames.of(record.getApprovedBy()));
        }
        dto.setApprovedAt(record.getApprovedAt());
        if (record.getRejectedBy() != null) {
            dto.setRejectedById(record.getRejectedBy().getId());
            dto.setRejectedByName(UserDisplayNames.of(record.getRejectedBy()));
        }
        dto.setRejectedAt(record.getRejectedAt());
        dto.setRejectionReason(record.getRejectionReason());
        dto.setReason(record.getReason());
        dto.setComplianceDocumentUrl(record.getComplianceDocumentUrl());
        dto.setOrganisationId(record.getOrganisation().getId());
        return dto;
    }

    private User resolveCurrentUser(Organisation org) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new AccessDeniedException("No authenticated user in security context");
        }
        User user = userRepository.findByEmailAndOrganisationId(auth.getName(), org.getId())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user not found in organisation"));
        if (user.getDeletedAt() != null || user.getStatus() != UserStatus.ACTIVE || user.isLockedOut()) {
            throw new AccessDeniedException("Authenticated user account is not active");
        }
        return user;
    }
}
