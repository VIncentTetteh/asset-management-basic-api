package com.assetiq.services;

import com.assetiq.dto.AssetCustomFieldDto;

import java.util.List;
import java.util.UUID;

public interface AssetCustomFieldService {

    AssetCustomFieldDto create(UUID assetId, AssetCustomFieldDto dto);

    List<AssetCustomFieldDto> listByAsset(UUID assetId);

    AssetCustomFieldDto update(UUID fieldId, AssetCustomFieldDto dto);

    void delete(UUID fieldId);
}
