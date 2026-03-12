package com.example.demo.services.impl;

import com.example.demo.dto.AssetTransferDto;
import com.example.demo.enums.TransferStatus;
import com.example.demo.models.AssetTransfer;
import com.example.demo.models.Asset;
import com.example.demo.models.Department;
import com.example.demo.models.Location;
import com.example.demo.models.Organisation;
import com.example.demo.models.User;
import com.example.demo.repositories.*;
import com.example.demo.enums.NotificationType;
import com.example.demo.services.AssetTransferService;
import com.example.demo.services.NotificationService;
import com.example.demo.services.TenantAwareService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AssetTransferServiceImpl extends TenantAwareService implements AssetTransferService {

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

        Department fromDept = departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(
                transferDto.getFromDepartmentId(), org)
                .orElseThrow(() -> new IllegalArgumentException("From-department not found in your organisation"));

        Department toDept = departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(
                transferDto.getToDepartmentId(), org)
                .orElseThrow(() -> new IllegalArgumentException("To-department not found in your organisation"));

        User requester = userRepository.findByIdAndOrganisation(transferDto.getRequestedById(), org)
                .orElseThrow(() -> new IllegalArgumentException("Requester not found in your organisation"));

        AssetTransfer transfer = new AssetTransfer();
        transfer.setAsset(asset);
        transfer.setFromDepartment(fromDept);
        transfer.setToDepartment(toDept);
        transfer.setRequestedBy(requester);
        transfer.setStatus(TransferStatus.REQUESTED);
        transfer.setReason(transferDto.getReason());

        if (transferDto.getFromLocationId() != null) {
            Location fromLoc = locationRepository.findByIdAndOrganisationAndDeletedAtIsNull(
                    transferDto.getFromLocationId(), org)
                    .orElseThrow(() -> new IllegalArgumentException("From-location not found in your organisation"));
            transfer.setFromLocation(fromLoc);
        }

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
                        + fromDept.getName() + " to " + toDept.getName() + ".",
                savedTransfer.getId(), "/api/v1/transfers/" + savedTransfer.getId());
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
        // C4 fix: resolve approver from current authenticated user
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            userRepository.findByEmailAndOrganisationId(auth.getName(), org.getId())
                    .ifPresent(transfer::setApprovedBy);
        }
        transfer.setStatus(TransferStatus.APPROVED);
        return mapToDto(transferRepository.save(transfer));
    }

    @Override
    public AssetTransferDto rejectTransfer(UUID id) {
        Organisation org = requireTenantOrg();
        AssetTransfer transfer = transferRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        if (!transfer.getAsset().getOrganisation().getId().equals(org.getId())) {
            throw new IllegalArgumentException("Transfer not found");
        }
        transfer.setStatus(TransferStatus.CANCELLED);
        return mapToDto(transferRepository.save(transfer));
    }

    @Override
    public AssetTransferDto completeTransfer(UUID id) {
        Organisation org = requireTenantOrg();
        AssetTransfer transfer = transferRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        if (!transfer.getAsset().getOrganisation().getId().equals(org.getId())) {
            throw new IllegalArgumentException("Transfer not found");
        }
        if (transfer.getStatus() != TransferStatus.APPROVED) {
            throw new IllegalStateException("Transfer must be approved before completion");
        }

        Asset asset = transfer.getAsset();
        asset.setDepartment(transfer.getToDepartment());
        if (transfer.getToLocation() != null) {
            asset.setLocation(transfer.getToLocation());
        }
        assetRepository.save(asset);

        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setTransferDate(LocalDate.now());

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
        transfer.setDeletedAt(Instant.now());
        transferRepository.save(transfer);
    }

    private AssetTransferDto mapToDto(AssetTransfer transfer) {
        AssetTransferDto dto = new AssetTransferDto();
        dto.setId(transfer.getId());
        dto.setAssetId(transfer.getAsset().getId());
        dto.setFromDepartmentId(transfer.getFromDepartment().getId());
        dto.setToDepartmentId(transfer.getToDepartment().getId());
        if (transfer.getFromLocation() != null) {
            dto.setFromLocationId(transfer.getFromLocation().getId());
        }
        if (transfer.getToLocation() != null) {
            dto.setToLocationId(transfer.getToLocation().getId());
        }
        dto.setRequestedById(transfer.getRequestedBy().getId());
        if (transfer.getApprovedBy() != null) {
            dto.setApprovedById(transfer.getApprovedBy().getId());
        }
        dto.setTransferDate(transfer.getTransferDate());
        dto.setStatus(transfer.getStatus());
        dto.setReason(transfer.getReason());
        return dto;
    }
}
