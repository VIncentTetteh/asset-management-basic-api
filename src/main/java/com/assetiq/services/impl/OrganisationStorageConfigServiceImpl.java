package com.assetiq.services.impl;

import com.assetiq.dto.OrganisationStorageConfigDto;
import com.assetiq.dto.OrganisationStorageConfigResponse;
import com.assetiq.models.Organisation;
import com.assetiq.models.OrganisationStorageConfig;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.OrganisationStorageConfigRepository;
import com.assetiq.services.OrganisationStorageConfigService;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OrganisationStorageConfigServiceImpl implements OrganisationStorageConfigService {

    private static final Logger log = LoggerFactory.getLogger(OrganisationStorageConfigServiceImpl.class);

    private final OrganisationStorageConfigRepository configRepository;
    private final OrganisationRepository organisationRepository;

    /** Server-wide default bucket, used when an organisation sets no override. */
    @org.springframework.beans.factory.annotation.Value("${app.storage.s3.bucket:}")
    private String globalBucket;

    void setGlobalBucket(String globalBucket) {
        this.globalBucket = globalBucket;
    }

    public OrganisationStorageConfigServiceImpl(
            OrganisationStorageConfigRepository configRepository,
            OrganisationRepository organisationRepository) {
        this.configRepository = configRepository;
        this.organisationRepository = organisationRepository;
    }

    @Override
    @Transactional
    public OrganisationStorageConfigResponse upsert(UUID organisationId, OrganisationStorageConfigDto dto) {
        Organisation org = requireOrg(organisationId);

        OrganisationStorageConfig config = configRepository
                .findByOrganisationIdAndDeletedAtIsNull(organisationId)
                .orElseGet(() -> {
                    OrganisationStorageConfig c = new OrganisationStorageConfig();
                    c.setOrganisation(org);
                    return c;
                });

        applyDto(config, dto);
        OrganisationStorageConfig saved = configRepository.save(config);

        log.info("[StorageConfig] Upserted storage config for org={} s3Enabled={}",
                organisationId, saved.isS3Enabled());
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OrganisationStorageConfigResponse get(UUID organisationId) {
        return toResponse(requireConfig(organisationId));
    }

    @Override
    @Transactional
    public OrganisationStorageConfigResponse toggleS3(UUID organisationId, boolean enabled) {
        OrganisationStorageConfig config = requireConfig(organisationId);
        config.setS3Enabled(enabled);
        OrganisationStorageConfig saved = configRepository.save(config);
        log.info("[StorageConfig] Toggled S3 for org={} enabled={}", organisationId, enabled);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID organisationId) {
        OrganisationStorageConfig config = requireConfig(organisationId);
        config.setDeletedAt(Instant.now());
        configRepository.save(config);
        log.info("[StorageConfig] Soft-deleted storage config for org={}", organisationId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applyDto(OrganisationStorageConfig config, OrganisationStorageConfigDto dto) {
        config.setS3Enabled(dto.isS3Enabled());

        // Blank clears the override (the global bucket applies). Saving the global
        // bucket's name as an override used to pin it to this organisation.
        if (dto.getBucketName() != null) {
            config.setBucketName(dto.getBucketName().isBlank() ? null : dto.getBucketName().trim());
        }

        if (dto.getReportPrefix() != null && !dto.getReportPrefix().isBlank()) {
            config.setReportPrefix(dto.getReportPrefix());
        }
        if (dto.getImportPrefix() != null && !dto.getImportPrefix().isBlank()) {
            config.setImportPrefix(dto.getImportPrefix());
        }
        if (dto.getPresignMinutes() != null) {
            config.setPresignMinutes(dto.getPresignMinutes());
        }
    }

    private OrganisationStorageConfigResponse toResponse(OrganisationStorageConfig config) {
        Organisation org = config.getOrganisation();
        return OrganisationStorageConfigResponse.builder()
                .id(config.getId())
                .organisationId(org.getId())
                .organisationName(org.getName())
                .s3Enabled(config.isS3Enabled())
                .bucketName(hasText(config.getBucketName()) ? config.getBucketName() : defaultBucket())
                .bucketOverride(hasText(config.getBucketName()) ? config.getBucketName() : null)
                .defaultBucket(defaultBucket())
                .reportPrefix(config.getReportPrefix())
                .importPrefix(config.getImportPrefix())
                .presignMinutes(config.getPresignMinutes())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .createdBy(config.getCreatedBy())
                .modifiedBy(config.getModifiedBy())
                .build();
    }

    private String defaultBucket() {
        return hasText(globalBucket) ? globalBucket.trim() : null;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private Organisation requireOrg(UUID organisationId) {
        return organisationRepository.findByIdAndDeletedAtIsNull(organisationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Organisation not found: " + organisationId));
    }

    private OrganisationStorageConfig requireConfig(UUID organisationId) {
        return configRepository.findByOrganisationIdAndDeletedAtIsNull(organisationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No storage configuration found for organisation: " + organisationId));
    }
}
