package com.example.demo.repositories.compliance;

import com.example.demo.models.Organisation;
import com.example.demo.models.compliance.RegulatoryFiling;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegulatoryFilingRepository extends JpaRepository<RegulatoryFiling, UUID> {

    List<RegulatoryFiling> findByOrganisationAndDeletedAtIsNullOrderByDueDateAsc(Organisation organisation);

    List<RegulatoryFiling> findByOrganisationAndStatusAndDeletedAtIsNull(
            Organisation organisation, RegulatoryFiling.FilingStatus status);

    Optional<RegulatoryFiling> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);
}
