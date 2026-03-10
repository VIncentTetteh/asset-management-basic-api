package com.example.demo.repositories.compliance;

import com.example.demo.models.Organisation;
import com.example.demo.models.compliance.BogControl;
import com.example.demo.models.compliance.ControlStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BogControlRepository extends JpaRepository<BogControl, UUID> {

    List<BogControl> findByOrganisationAndDeletedAtIsNull(Organisation organisation);

    List<BogControl> findByOrganisationAndStatusAndDeletedAtIsNull(
            Organisation organisation, ControlStatus status);

    Optional<BogControl> findByOrganisationAndDirectiveRefAndDeletedAtIsNull(
            Organisation organisation, String directiveRef);

    Optional<BogControl> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    long countByOrganisationAndStatusAndDeletedAtIsNull(Organisation organisation, ControlStatus status);
}
