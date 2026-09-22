package com.assetiq.services.impl;

import com.assetiq.dto.AssetTransferDto;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.TransferStatus;
import com.assetiq.enums.UserStatus;
import com.assetiq.models.AssetTransfer;
import com.assetiq.models.Asset;
import com.assetiq.models.Department;
import com.assetiq.models.Location;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import com.assetiq.repositories.*;
import com.assetiq.enums.NotificationType;
import com.assetiq.services.AssetTransferService;
import com.assetiq.services.NotificationService;
import com.assetiq.services.TenantAwareService;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AssetTransferServiceImpl extends TenantAwareService implements AssetTransferService {

    /** Transfers that still block another request for the same asset. */
    private static final Set<TransferStatus> OPEN = EnumSet.of(TransferStatus.REQUESTED, TransferStatus.APPROVED,
            TransferStatus.IN_TRANSIT);

    private final AssetTransferRepository transferRepository;
    private final AssetRepository assetRepository;
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public AssetTransferServiceImpl(AssetTransferRepository transferRepository,
            AssetRepository assetRepository,
            DepartmentRepository departmentRepository,
            LocationRepository locationRepository,
            UserRepository userRepository,
            OrganisationRepository organisationRepository,
            NotificationService notificationService) {
        super(organisationRepository);
        this.transferRepository = transferRepository;
        this.assetRepository = assetRepository;
        this.departmentRepository = departmentRepository;
        this.locationRepository = locationRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Override
    public AssetTransferDto createTransferRequest(AssetTransferDto transferDto) {
        Organisation org = requireTenantOrg();

        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(transferDto.getAssetId(), org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found in your organisation"));

        // The origin is where the asset is now, read from the asset itself. It used
        // to be taken from the request, so a client could record any origin; an
        // asset with no department could not be transferred at all.
        Department fromDept = asset.getDepartment();
        Location fromLoc = asset.getLocation();

        Department toDept = departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(
                transferDto.getToDepartmentId(), org)
                .orElseThrow(() -> new IllegalArgumentException("To-department not found in your organisation"));

        if (fromDept != null && fromDept.getId().equals(toDept.getId())) {
            throw new IllegalArgumentException("The destination department must differ from the source department");
        }
        if (asset.getStatus() == AssetStatus.DISPOSED || asset.getStatus() == AssetStatus.RETIRED) {
            throw new IllegalStateException("Asset '" + asset.getName() + "' is " + asset.getStatus()
                    + " and cannot be transferred");
        }
        boolean openTransfer = transferRepository.findByAssetIdAndDeletedAtIsNull(asset.getId()).stream()
                .anyMatch(t -> OPEN.contains(t.getStatus()));
        if (openTransfer) {
            throw new IllegalStateException("Asset '" + asset.getName()
                    + "' already has an open transfer; complete or reject it first");
        }

        User requester = resolveCurrentUser(org);

        AssetTransfer transfer = new AssetTransfer();
        transfer.setAsset(asset);
        transfer.setFromDepartment(fromDept);
        transfer.setToDepartment(toDept);
        transfer.setRequestedBy(requester);
        transfer.setStatus(TransferStatus.REQUESTED);
        transfer.setReason(transferDto.getReason());

        transfer.setFromLocation(fromLoc);

        if (transferDto.getToLocationId() != null) {
            Location toLoc = locationRepository.findByIdAndOrganisationAndDeletedAtIsNull(
                    transferDto.getToLocationId(), org)
                    .orElseThrow(() -> new IllegalArgumentException("To-location not found in your organisation"));
            transfer.setToLocation(toLoc);
        }

        transfer.setOrganisation(org);
        AssetTransfer savedTransfer = transferRepository.save(transfer);
        notificationService.notifyOrgAdmins(org, NotificationType.TRANSFER,
                "Asset Transfer Requested",
                "A transfer request has been submitted for asset '" + asset.getName() + "' from "
                        + (fromDept != null ? fromDept.getName() : "no department") + " to " + toDept.getName() + ".",
                savedTransfer.getId(), "/transfers");
        return mapToDto(savedTransfer);
    }

    @Override
    @Transactional(readOnly = true)
    public AssetTransferDto getTransferById(UUID id) {
        Organisation org = requireTenantOrg();
        AssetTransfer transfer = transferRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        if (!transfer.getOrganisation().getId().equals(org.getId())) {
            throw new IllegalArgumentException("Transfer not found");
        }
        return mapToDto(transfer);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<AssetTransferDto> getAllTransfers() {
        Organisation org = requireTenantOrg();
        return transferRepository.findByOrganisationAndDeletedAtIsNull(org).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<AssetTransferDto> getTransfersByAsset(UUID assetId) {
        Organisation org = requireTenantOrg();
        assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found in your organisation"));
        return transferRepository.findByAssetIdAndDeletedAtIsNull(assetId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<AssetTransferDto> getTransfersFromDepartment(UUID departmentId) {
        Organisation org = requireTenantOrg();
        departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(departmentId, org)
                .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));
        return transferRepository.findByFromDepartmentIdAndDeletedAtIsNull(departmentId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<AssetTransferDto> getTransfersToDepartment(UUID departmentId) {
        Organisation org = requireTenantOrg();
        departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(departmentId, org)
                .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));
        return transferRepository.findByToDepartmentIdAndDeletedAtIsNull(departmentId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<AssetTransferDto> getTransfersByRequester(UUID userId) {
        Organisation org = requireTenantOrg();
        userRepository.findByIdAndOrganisation(userId, org)
                .orElseThrow(() -> new IllegalArgumentException("User not found in your organisation"));
        return transferRepository.findByRequestedByIdAndDeletedAtIsNull(userId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    public AssetTransferDto approveTransfer(UUID id) {
        Organisation org = requireTenantOrg();
        AssetTransfer transfer = transferRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        if (!transfer.getOrganisation().getId().equals(org.getId())) {
            throw new IllegalArgumentException("Transfer not found");
        }
        requireStatus(transfer, "approved", TransferStatus.REQUESTED);
        User approver = resolveCurrentUser(org);
        if (transfer.getRequestedBy().getId().equals(approver.getId())) {
            throw new AccessDeniedException("Transfer requests require approval by a different user");
        }
        transfer.setApprovedBy(approver);
        transfer.setStatus(TransferStatus.APPROVED);
        return mapToDto(transferRepository.save(transfer));
    }

    @Override
    public AssetTransferDto rejectTransfer(UUID id) {
        Organisation org = requireTenantOrg();
        AssetTransfer transfer = transferRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        if (!transfer.getOrganisation().getId().equals(org.getId())) {
            throw new IllegalArgumentException("Transfer not found");
        }
        // An approved transfer can still be stopped before the asset moves.
        requireStatus(transfer, "rejected", TransferStatus.REQUESTED, TransferStatus.APPROVED);
        // REJECTED, not CANCELLED: the UI and reports distinguish a refused request.
        transfer.setStatus(TransferStatus.REJECTED);
        return mapToDto(transferRepository.save(transfer));
    }

    @Override
    public AssetTransferDto completeTransfer(UUID id) {
        Organisation org = requireTenantOrg();
        AssetTransfer transfer = transferRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        if (!transfer.getOrganisation().getId().equals(org.getId())) {
            throw new IllegalArgumentException("Transfer not found");
        }
        if (transfer.getStatus() != TransferStatus.APPROVED) {
            throw new IllegalStateException("Transfer must be approved before completion (it is "
                    + transfer.getStatus() + ")");
        }
        User completer = resolveCurrentUser(org);

        Asset asset = transfer.getAsset();
        if (asset.getStatus() == AssetStatus.DISPOSED || asset.getStatus() == AssetStatus.RETIRED) {
            throw new IllegalStateException("Asset '" + asset.getName() + "' is " + asset.getStatus()
                    + " and cannot be transferred");
        }
        asset.setDepartment(transfer.getToDepartment());
        if (transfer.getToLocation() != null) {
            asset.setLocation(transfer.getToLocation());
        }
        assetRepository.save(asset);

        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setTransferDate(LocalDate.now());
        transfer.setCompletedBy(completer);

        return mapToDto(transferRepository.save(transfer));
    }

    @Override
    public void deleteTransfer(UUID id) {
        Organisation org = requireTenantOrg();
        AssetTransfer transfer = transferRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        // M3 fix: check organisation FK directly
        if (!transfer.getOrganisation().getId().equals(org.getId())) {
            throw new IllegalArgumentException("Transfer not found");
        }
        if (transfer.getStatus() == TransferStatus.COMPLETED) {
            // The completed transfer is the audit trail for the asset's move.
            throw new IllegalStateException("Completed transfers cannot be deleted");
        }
        transfer.setDeletedAt(Instant.now());
        transferRepository.save(transfer);
    }

    private static void requireStatus(AssetTransfer transfer, String action, TransferStatus... allowed) {
        for (TransferStatus status : allowed) {
            if (transfer.getStatus() == status) return;
        }
        throw new IllegalStateException("A " + transfer.getStatus() + " transfer cannot be " + action);
    }

    private AssetTransferDto mapToDto(AssetTransfer transfer) {
        AssetTransferDto dto = new AssetTransferDto();
        dto.setId(transfer.getId());
        dto.setAssetId(transfer.getAsset().getId());
        if (transfer.getFromDepartment() != null) {
            dto.setFromDepartmentId(transfer.getFromDepartment().getId());
        }
        dto.setToDepartmentId(transfer.getToDepartment().getId());
        if (transfer.getFromLocation() != null) {
            dto.setFromLocationId(transfer.getFromLocation().getId());
        }
        if (transfer.getToLocation() != null) {
            dto.setToLocationId(transfer.getToLocation().getId());
        }
        dto.setRequestedById(transfer.getRequestedBy().getId());
        dto.setRequestedByName(displayName(transfer.getRequestedBy()));
        if (transfer.getApprovedBy() != null) {
            dto.setApprovedById(transfer.getApprovedBy().getId());
            dto.setApprovedByName(displayName(transfer.getApprovedBy()));
        }
        if (transfer.getCompletedBy() != null) {
            dto.setCompletedById(transfer.getCompletedBy().getId());
            dto.setCompletedByName(displayName(transfer.getCompletedBy()));
        }
        dto.setTransferDate(transfer.getTransferDate());
        dto.setCreatedAt(transfer.getCreatedAt());
        dto.setStatus(transfer.getStatus());
        dto.setReason(transfer.getReason());
        return dto;
    }

    /** A user's full name, or their email when no name is on file. */
    static String displayName(User user) {
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return name.isEmpty() ? user.getEmail() : name;
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
