package com.assetiq.repositories;

import com.assetiq.models.CustomFieldDefinition;
import com.assetiq.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Custom field definitions, always read through the owning organisation.
 *
 * <p>There is deliberately no {@code findByFieldKey} without an organisation: a
 * definition is tenant data, and a finder that could be called without the tenant is a
 * cross-tenant read waiting to be written.</p>
 */
@Repository
public interface CustomFieldDefinitionRepository extends JpaRepository<CustomFieldDefinition, UUID> {

    List<CustomFieldDefinition> findByOrganisationAndEntityTypeAndDeletedAtIsNullOrderByFieldNameAsc(
            Organisation organisation, String entityType);

    Optional<CustomFieldDefinition> findByOrganisationAndEntityTypeAndFieldKeyAndDeletedAtIsNull(
            Organisation organisation, String entityType, String fieldKey);

    long countByOrganisationAndEntityTypeAndDeletedAtIsNull(Organisation organisation, String entityType);
}
