package com.example.demo.services;

import com.example.demo.dto.AssetTransferDto;
import java.util.Set;
import java.util.UUID;

public interface AssetTransferService {
    AssetTransferDto createTransferRequest(AssetTransferDto transferDto);
    AssetTransferDto getTransferById(UUID id);
    Set<AssetTransferDto> getTransfersByAsset(UUID assetId);
    Set<AssetTransferDto> getTransfersFromDepartment(UUID departmentId);
    Set<AssetTransferDto> getTransfersToDepartment(UUID departmentId);
    Set<AssetTransferDto> getTransfersByRequester(UUID userId);
    AssetTransferDto approveTransfer(UUID id, UUID approvedById);
    AssetTransferDto rejectTransfer(UUID id);
    AssetTransferDto completeTransfer(UUID id);
    void deleteTransfer(UUID id);
}

