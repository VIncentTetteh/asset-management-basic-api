package com.example.demo.services;

import com.example.demo.dto.AssetDto;

import java.util.List;
import java.util.UUID;

public interface AssetService {
    AssetDto create(AssetDto dto);
    AssetDto get(UUID id);
    List<AssetDto> list();
    AssetDto assignToDepartment(UUID assetId, UUID departmentId);
    AssetDto update(UUID id, AssetDto dto);
    void delete(UUID id);
}
