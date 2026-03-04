package com.example.demo.repositories;

import com.example.demo.models.DepreciationPolicy;
import com.example.demo.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface DepreciationPolicyRepository extends JpaRepository<DepreciationPolicy, UUID> {
    Optional<DepreciationPolicy> findByNameAndOrganisationId(String name, UUID organisationId);

    Set<DepreciationPolicy> findByOrganisationId(UUID organisationId);

    // Tenant + soft-delete scoped
    Optional<DepreciationPolicy> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    Set<DepreciationPolicy> findByOrganisationAndDeletedAtIsNull(Organisation organisation);
}
