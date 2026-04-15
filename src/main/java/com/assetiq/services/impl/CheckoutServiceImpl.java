package com.assetiq.services.impl;

import com.assetiq.dto.CheckoutRecordDto;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.CheckoutStatus;
import com.assetiq.enums.NotificationType;
import com.assetiq.models.*;
import com.assetiq.repositories.*;
import com.assetiq.services.AssetStateTransitionService;
import com.assetiq.services.CheckoutService;
import com.assetiq.services.NotificationService;
import com.assetiq.services.TenantAwareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class CheckoutServiceImpl extends TenantAwareService implements CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutServiceImpl.class);

    private final CheckoutRecordRepository checkoutRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AssetStateTransitionService stateTransitionService;

    public CheckoutServiceImpl(CheckoutRecordRepository checkoutRepository,
                               AssetRepository assetRepository,
                               UserRepository userRepository,
                               OrganisationRepository organisationRepository,
                               NotificationService notificationService,
                               AssetStateTransitionService stateTransitionService) {
        super(organisationRepository);
        this.checkoutRepository = checkoutRepository;
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.stateTransitionService = stateTransitionService;
    }

    // ── Check-Out ─────────────────────────────────────────────────────────────

    @Override
    public CheckoutRecordDto checkOut(UUID assetId, UUID userId, CheckoutRecordDto dto) {
        Organisation org = requireTenantOrg();

        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));

        // Prevent double checkout
        checkoutRepository.findByAssetAndStatusAndDeletedAtIsNull(asset, CheckoutStatus.ACTIVE)
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Asset '" + asset.getName() + "' is already checked out. Check it in first.");
                });

        User user = userRepository.findByIdAndOrganisationAndDeletedAtIsNull(userId, org)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        CheckoutRecord record = new CheckoutRecord();
        record.setAsset(asset);
        record.setCheckedOutBy(user);
        record.setCheckedOutAt(Instant.now());
        record.setExpectedReturnDate(dto != null ? dto.getExpectedReturnDate() : null);
        record.setConditionOnCheckout(dto != null ? dto.getConditionOnCheckout() : null);
        record.setNotes(dto != null ? dto.getNotes() : null);
        record.setStatus(CheckoutStatus.ACTIVE);
        record.setOrganisation(org);

        // Transition asset to IN_USE (allowed from IN_STOCK, RESERVED, etc.)
        if (asset.getStatus() != AssetStatus.IN_USE) {
            stateTransitionService.transition(asset, AssetStatus.IN_USE, user, "Checked out to " + user.getEmail());
        }

        CheckoutRecord saved = checkoutRepository.save(record);

        notificationService.notifyOrgAdmins(org, NotificationType.CHECKOUT,
                "Asset Checked Out",
                "Asset '" + asset.getName() + "' has been checked out to " + user.getFirstName()
                        + " " + user.getLastName() + ".",
                saved.getId(), null);

        log.info("Asset {} checked out to user {} (record={})", assetId, userId, saved.getId());
        return toDto(saved);
    }

    // ── Check-In ──────────────────────────────────────────────────────────────

    @Override
    public CheckoutRecordDto checkIn(UUID checkoutRecordId, CheckoutRecordDto dto) {
        Organisation org = requireTenantOrg();

        CheckoutRecord record = checkoutRepository.findById(checkoutRecordId)
                .filter(r -> r.getOrganisation().getId().equals(org.getId()) && r.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("Checkout record not found: " + checkoutRecordId));

        if (record.getStatus() == CheckoutStatus.RETURNED) {
            throw new IllegalStateException("This asset has already been returned.");
        }

        // Resolve the user processing the return from the security context
        User checkedInBy = resolveCurrentUser(org);

        record.setActualReturnDate(LocalDate.now());
        record.setConditionOnReturn(dto != null ? dto.getConditionOnReturn() : null);
        record.setCheckedInBy(checkedInBy);
        record.setStatus(CheckoutStatus.RETURNED);

        Asset asset = record.getAsset();
        stateTransitionService.transition(asset, AssetStatus.IN_STOCK, checkedInBy, "Checked back in");

        CheckoutRecord saved = checkoutRepository.save(record);

        notificationService.notifyOrgAdmins(org, NotificationType.CHECKOUT,
                "Asset Returned",
                "Asset '" + asset.getName() + "' has been returned by "
                        + record.getCheckedOutBy().getFirstName() + " "
                        + record.getCheckedOutBy().getLastName() + ".",
                saved.getId(), null);

        log.info("Asset {} checked in (record={})", asset.getId(), saved.getId());
        return toDto(saved);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public CheckoutRecordDto getById(UUID id) {
        Organisation org = requireTenantOrg();
        CheckoutRecord record = checkoutRepository.findById(id)
                .filter(r -> r.getOrganisation().getId().equals(org.getId()) && r.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("Checkout record not found: " + id));
        return toDto(record);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckoutRecordDto> listByOrg() {
        Organisation org = requireTenantOrg();
        return checkoutRepository.findByOrganisationAndDeletedAtIsNull(org)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckoutRecordDto> listByAsset(UUID assetId) {
        Organisation org = requireTenantOrg();
        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));
        return checkoutRepository.findByAssetAndDeletedAtIsNull(asset)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckoutRecordDto> listByUser(UUID userId) {
        Organisation org = requireTenantOrg();
        User user = userRepository.findByIdAndOrganisationAndDeletedAtIsNull(userId, org)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return checkoutRepository.findByCheckedOutByAndDeletedAtIsNull(user)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckoutRecordDto> listOverdue() {
        Organisation org = requireTenantOrg();
        LocalDate today = LocalDate.now();
        return checkoutRepository.findByOrganisationAndStatusAndDeletedAtIsNull(org, CheckoutStatus.ACTIVE)
                .stream()
                .filter(r -> r.getExpectedReturnDate() != null && r.getExpectedReturnDate().isBefore(today))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User resolveCurrentUser(Organisation org) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            return userRepository.findByEmailAndOrganisationId(auth.getName(), org.getId()).orElse(null);
        }
        return null;
    }

    private CheckoutRecordDto toDto(CheckoutRecord r) {
        CheckoutRecordDto dto = new CheckoutRecordDto();
        dto.setId(r.getId());
        dto.setAssetId(r.getAsset().getId());
        dto.setAssetName(r.getAsset().getName());
        dto.setCheckedOutById(r.getCheckedOutBy().getId());
        dto.setCheckedOutByName(r.getCheckedOutBy().getFirstName() + " " + r.getCheckedOutBy().getLastName());
        dto.setCheckedOutAt(r.getCheckedOutAt());
        dto.setExpectedReturnDate(r.getExpectedReturnDate());
        dto.setActualReturnDate(r.getActualReturnDate());
        if (r.getCheckedInBy() != null) {
            dto.setCheckedInById(r.getCheckedInBy().getId());
        }
        dto.setConditionOnCheckout(r.getConditionOnCheckout());
        dto.setConditionOnReturn(r.getConditionOnReturn());
        dto.setNotes(r.getNotes());
        dto.setStatus(r.getStatus());
        dto.setOrganisationId(r.getOrganisation().getId());
        return dto;
    }
}
