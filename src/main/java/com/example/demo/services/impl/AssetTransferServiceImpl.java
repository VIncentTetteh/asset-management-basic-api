package com.example.demo.services.impl;

import com.example.demo.dto.AssetTransferDto;
import com.example.demo.enums.TransferStatus;
import com.example.demo.enums.AssetStatus;
import com.example.demo.models.AssetTransfer;
import com.example.demo.models.Asset;
import com.example.demo.models.Department;
import com.example.demo.models.Location;
import com.example.demo.models.User;
import com.example.demo.repositories.*;
import com.example.demo.services.AssetTransferService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AssetTransferServiceImpl implements AssetTransferService {

    private final AssetTransferRepository transferRepository;
    private final AssetRepository assetRepository;
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;

    public AssetTransferServiceImpl(AssetTransferRepository transferRepository,
                                  AssetRepository assetRepository,
                                  DepartmentRepository departmentRepository,
                                  LocationRepository locationRepository,
                                  UserRepository userRepository) {
        this.transferRepository = transferRepository;
        this.assetRepository = assetRepository;
        this.departmentRepository = departmentRepository;
        this.locationRepository = locationRepository;
        this.userRepository = userRepository;
    }

    @Override
    public AssetTransferDto createTransferRequest(AssetTransferDto transferDto) {
        Asset asset = assetRepository.findById(transferDto.getAssetId())
            .orElseThrow(() -> new IllegalArgumentException("Asset not found"));
        Department fromDept = departmentRepository.findById(transferDto.getFromDepartmentId())
            .orElseThrow(() -> new IllegalArgumentException("From department not found"));
        Department toDept = departmentRepository.findById(transferDto.getToDepartmentId())
            .orElseThrow(() -> new IllegalArgumentException("To department not found"));
        User requester = userRepository.findById(transferDto.getRequestedById())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        AssetTransfer transfer = new AssetTransfer();
        transfer.setAsset(asset);
        transfer.setFromDepartment(fromDept);
        transfer.setToDepartment(toDept);
        transfer.setRequestedBy(requester);
        transfer.setStatus(TransferStatus.REQUESTED);
        transfer.setReason(transferDto.getReason());

        if (transferDto.getFromLocationId() != null) {
            transfer.setFromLocation(locationRepository.findById(transferDto.getFromLocationId())
                .orElseThrow(() -> new IllegalArgumentException("From location not found")));
        }

        if (transferDto.getToLocationId() != null) {
            transfer.setToLocation(locationRepository.findById(transferDto.getToLocationId())
                .orElseThrow(() -> new IllegalArgumentException("To location not found")));
        }

        AssetTransfer savedTransfer = transferRepository.save(transfer);
        return mapToDto(savedTransfer);
    }

    @Override
    @Transactional(readOnly = true)
    public AssetTransferDto getTransferById(UUID id) {
        AssetTransfer transfer = transferRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        return mapToDto(transfer);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<AssetTransferDto> getTransfersByAsset(UUID assetId) {
        return transferRepository.findByAssetId(assetId).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<AssetTransferDto> getTransfersFromDepartment(UUID departmentId) {
        return transferRepository.findByFromDepartmentId(departmentId).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<AssetTransferDto> getTransfersToDepartment(UUID departmentId) {
        return transferRepository.findByToDepartmentId(departmentId).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<AssetTransferDto> getTransfersByRequester(UUID userId) {
        return transferRepository.findByRequestedById(userId).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    public AssetTransferDto approveTransfer(UUID id, UUID approvedById) {
        AssetTransfer transfer = transferRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        User approver = userRepository.findById(approvedById)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        transfer.setStatus(TransferStatus.APPROVED);
        transfer.setApprovedBy(approver);

        AssetTransfer updatedTransfer = transferRepository.save(transfer);
        return mapToDto(updatedTransfer);
    }

    @Override
    public AssetTransferDto rejectTransfer(UUID id) {
        AssetTransfer transfer = transferRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));

        transfer.setStatus(TransferStatus.CANCELLED);

        AssetTransfer updatedTransfer = transferRepository.save(transfer);
        return mapToDto(updatedTransfer);
    }

    @Override
    public AssetTransferDto completeTransfer(UUID id) {
        AssetTransfer transfer = transferRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));

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

        AssetTransfer updatedTransfer = transferRepository.save(transfer);
        return mapToDto(updatedTransfer);
    }

    @Override
    public void deleteTransfer(UUID id) {
        transferRepository.deleteById(id);
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

