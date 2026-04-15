package com.assetiq.services;

import com.assetiq.dto.AssetDto;
import com.assetiq.dto.AssetHistoryEventDto;
import com.assetiq.dto.TcoDto;
import com.assetiq.enums.AssetStatus;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface AssetService {
    AssetDto create(AssetDto dto);

    AssetDto get(UUID id);

    List<AssetDto> list();

    Set<AssetDto> listByStatus(AssetStatus status);

    Set<AssetDto> listByDepartment(UUID departmentId);

    Set<AssetDto> listByCategory(UUID categoryId);

    AssetDto assignToDepartment(UUID assetId, UUID departmentId);
    AssetDto assignToUser(UUID assetId, UUID userId);
    AssetDto unassignUser(UUID assetId);

    AssetDto update(UUID id, AssetDto dto);
    AssetDto patch(UUID id, AssetDto dto);

    void delete(UUID id);

    List<AssetHistoryEventDto> getHistory(UUID assetId);

    /**
     * Calculate the Total Cost of Ownership for an asset.
     * Aggregates acquisition cost, maintenance costs, insurance premiums, downtime costs,
     * and subtracts disposal/sale proceeds if the asset has been disposed.
     */
    TcoDto getTco(UUID assetId);

    /**
     * Look up an asset by the decoded QR payload (e.g. "asset:<uuid>").
     * Returns the full AssetDto if found and belongs to the current tenant.
     */
    AssetDto getByQrPayload(String payload);
}
