package com.example.demo.repositories;

import com.example.demo.models.Location;
import com.example.demo.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {
    Optional<Location> findByNameAndOrganisationId(String name, UUID organisationId);

    Set<Location> findByOrganisationId(UUID organisationId);

    Set<Location> findByParentLocationId(UUID parentLocationId);

    // Tenant + soft-delete scoped
    Optional<Location> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    Set<Location> findByOrganisationAndDeletedAtIsNull(Organisation organisation);

    Set<Location> findByParentLocationIdAndDeletedAtIsNull(UUID parentLocationId);
}
