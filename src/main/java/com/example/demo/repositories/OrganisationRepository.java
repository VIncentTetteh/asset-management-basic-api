package com.example.demo.repositories;

import com.example.demo.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganisationRepository extends JpaRepository<Organisation, UUID> {
    List<Organisation> findAllByDeletedAtIsNull();

    List<Organisation> findAllByCreatedByAndDeletedAtIsNull(String createdBy);

    Optional<Organisation> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Organisation> findByNameIgnoreCaseAndDeletedAtIsNull(String name);

    boolean existsByNameIgnoreCaseAndDeletedAtIsNull(String name);
}
