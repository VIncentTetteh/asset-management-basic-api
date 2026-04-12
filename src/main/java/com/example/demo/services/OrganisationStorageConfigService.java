package com.example.demo.services;

import com.example.demo.dto.OrganisationStorageConfigDto;
import com.example.demo.dto.OrganisationStorageConfigResponse;

import java.util.UUID;

public interface OrganisationStorageConfigService {

    /**
     * Create or update the S3 storage configuration for the given organisation.
     */
    OrganisationStorageConfigResponse upsert(UUID organisationId, OrganisationStorageConfigDto dto);

    /**
     * Retrieve the current storage configuration for the given organisation.
     *
     * @throws jakarta.persistence.EntityNotFoundException when no config exists
     */
    OrganisationStorageConfigResponse get(UUID organisationId);

    /**
     * Enable or disable S3 for the given organisation without touching any
     * other configuration fields.
     *
     * @throws jakarta.persistence.EntityNotFoundException when no config exists
     */
    OrganisationStorageConfigResponse toggleS3(UUID organisationId, boolean enabled);

    /**
     * Soft-delete the storage configuration for the given organisation.
     * After deletion the organisation reverts to the in-memory fallback.
     *
     * @throws jakarta.persistence.EntityNotFoundException when no config exists
     */
    void delete(UUID organisationId);
}
