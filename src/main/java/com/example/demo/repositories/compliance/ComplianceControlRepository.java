package com.example.demo.repositories.compliance;

import com.example.demo.models.Organisation;
import com.example.demo.models.compliance.ComplianceControl;
import com.example.demo.models.compliance.ComplianceFramework;
import com.example.demo.models.compliance.ControlStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceControlRepository extends JpaRepository<ComplianceControl, UUID> {

    List<ComplianceControl> findByOrganisationAndDeletedAtIsNull(Organisation organisation);

    List<ComplianceControl> findByOrganisationAndFrameworkAndDeletedAtIsNull(
            Organisation organisation, ComplianceFramework framework);

    List<ComplianceControl> findByOrganisationAndFrameworkAndStatusAndDeletedAtIsNull(
            Organisation organisation, ComplianceFramework framework, ControlStatus status);

    Optional<ComplianceControl> findByOrganisationAndFrameworkAndControlRefAndDeletedAtIsNull(
            Organisation organisation, ComplianceFramework framework, String controlRef);

    long countByOrganisationAndFrameworkAndDeletedAtIsNull(Organisation organisation, ComplianceFramework framework);

    long countByOrganisationAndFrameworkAndStatusAndDeletedAtIsNull(
            Organisation organisation, ComplianceFramework framework, ControlStatus status);

    Optional<ComplianceControl> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);
}
