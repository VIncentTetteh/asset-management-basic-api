package com.example.demo.repositories;

import com.example.demo.models.Supplier;
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
}

