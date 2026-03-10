package com.example.demo.repositories.compliance;

import com.example.demo.models.Organisation;
import com.example.demo.models.compliance.SecurityIncident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SecurityIncidentRepository extends JpaRepository<SecurityIncident, UUID> {

    Page<SecurityIncident> findByOrganisationAndDeletedAtIsNull(Organisation organisation, Pageable pageable);

    long countByOrganisationAndStatusAndDeletedAtIsNull(
            Organisation organisation, SecurityIncident.IncidentStatus status);

    Optional<SecurityIncident> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);
}
