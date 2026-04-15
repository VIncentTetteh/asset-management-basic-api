package com.assetiq.repositories.compliance;

import com.assetiq.models.Organisation;
import com.assetiq.models.compliance.RegulatoryFiling;
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
