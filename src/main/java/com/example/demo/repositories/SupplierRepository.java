package com.example.demo.repositories;

import com.example.demo.models.Supplier;
import com.example.demo.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    Optional<Supplier> findByNameAndOrganisationId(String name, UUID organisationId);

    Set<Supplier> findByOrganisationId(UUID organisationId);

    Optional<Supplier> findByEmailAndOrganisationId(String email, UUID organisationId);

    // Tenant + soft-delete scoped
    Optional<Supplier> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    Set<Supplier> findByOrganisationAndDeletedAtIsNull(Organisation organisation);

    Optional<Supplier> findByEmailAndOrganisationAndDeletedAtIsNull(String email, Organisation organisation);

    boolean existsByIdAndOrganisation(UUID id, Organisation organisation);
}
