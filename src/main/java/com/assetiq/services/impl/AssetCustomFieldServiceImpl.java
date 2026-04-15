package com.assetiq.services.impl;

import com.assetiq.dto.AssetCustomFieldDto;
import com.assetiq.models.Asset;
import com.assetiq.models.AssetCustomField;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.AssetCustomFieldRepository;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.AssetCustomFieldService;
import com.assetiq.services.TenantAwareService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AssetCustomFieldServiceImpl extends TenantAwareService implements AssetCustomFieldService {

    private final AssetCustomFieldRepository fieldRepository;
    private final AssetRepository assetRepository;

    public AssetCustomFieldServiceImpl(OrganisationRepository organisationRepository,
                                       AssetCustomFieldRepository fieldRepository,
                                       AssetRepository assetRepository) {
        super(organisationRepository);
        this.fieldRepository = fieldRepository;
        this.assetRepository = assetRepository;
    }

    @Override
    @Transactional
    public AssetCustomFieldDto create(UUID assetId, AssetCustomFieldDto dto) {
        Organisation org = requireTenantOrg();
        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));

        if (fieldRepository.existsByAssetIdAndFieldNameAndDeletedAtIsNull(assetId, dto.getFieldName())) {
            throw new IllegalStateException("Field '" + dto.getFieldName() + "' already exists on this asset");
        }

        AssetCustomField field = new AssetCustomField();
        field.setAsset(asset);
        field.setFieldName(dto.getFieldName().trim());
        field.setFieldValue(dto.getFieldValue());
        field.setOrganisation(org);

        return toDto(fieldRepository.save(field));
    }

    @Override
    public List<AssetCustomFieldDto> listByAsset(UUID assetId) {
        Organisation org = requireTenantOrg();
        assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));

        return fieldRepository.findByAssetIdAndDeletedAtIsNull(assetId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AssetCustomFieldDto update(UUID fieldId, AssetCustomFieldDto dto) {
        Organisation org = requireTenantOrg();
        AssetCustomField field = fieldRepository.findByIdAndOrganisationAndDeletedAtIsNull(fieldId, org)
                .orElseThrow(() -> new IllegalArgumentException("Custom field not found: " + fieldId));

        if (dto.getFieldName() != null && !dto.getFieldName().isBlank()) {
            field.setFieldName(dto.getFieldName().trim());
        }
        field.setFieldValue(dto.getFieldValue());

        return toDto(fieldRepository.save(field));
    }

    @Override
    @Transactional
    public void delete(UUID fieldId) {
        Organisation org = requireTenantOrg();
        AssetCustomField field = fieldRepository.findByIdAndOrganisationAndDeletedAtIsNull(fieldId, org)
                .orElseThrow(() -> new IllegalArgumentException("Custom field not found: " + fieldId));
        field.setDeletedAt(Instant.now());
        fieldRepository.save(field);
    }

    private AssetCustomFieldDto toDto(AssetCustomField f) {
        AssetCustomFieldDto d = new AssetCustomFieldDto();
        d.setId(f.getId());
        d.setAssetId(f.getAsset().getId());
        d.setFieldName(f.getFieldName());
        d.setFieldValue(f.getFieldValue());
        return d;
    }
}
