package com.example.demo.repositories;

import com.example.demo.models.Location;
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
}

