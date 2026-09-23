package com.assetiq.repositories;

import com.assetiq.models.ImportMappingPreset;
import com.assetiq.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportMappingPresetRepository extends JpaRepository<ImportMappingPreset, UUID> {

    List<ImportMappingPreset> findByOrganisationAndEntityTypeAndDeletedAtIsNullOrderByNameAsc(
            Organisation organisation, String entityType);

    Optional<ImportMappingPreset> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    Optional<ImportMappingPreset> findByOrganisationAndEntityTypeAndNameIgnoreCaseAndDeletedAtIsNull(
            Organisation organisation, String entityType, String name);
}
