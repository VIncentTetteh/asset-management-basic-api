package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class AssetCustomFieldDto {

    private UUID id;

    private UUID assetId;

    @NotBlank
    @Size(max = 100)
    private String fieldName;

    private String fieldValue;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAssetId() { return assetId; }
    public void setAssetId(UUID assetId) { this.assetId = assetId; }

    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }

    public String getFieldValue() { return fieldValue; }
    public void setFieldValue(String fieldValue) { this.fieldValue = fieldValue; }
}
