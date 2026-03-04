package com.example.demo.repositories;

import com.example.demo.models.Role;
import com.example.demo.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByNameAndOrganisationId(String name, UUID organisationId);

    Set<Role> findByOrganisationId(UUID organisationId);

    // Tenant + soft-delete scoped
    Optional<Role> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    Set<Role> findByOrganisationAndDeletedAtIsNull(Organisation organisation);

    Optional<Role> findByNameAndOrganisationAndDeletedAtIsNull(String name, Organisation organisation);
}
