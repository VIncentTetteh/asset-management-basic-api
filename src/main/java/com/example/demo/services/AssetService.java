package com.example.demo.services;

import com.example.demo.dto.AssetDto;
import com.example.demo.dto.AssetHistoryEventDto;
import com.example.demo.enums.AssetStatus;

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
}
