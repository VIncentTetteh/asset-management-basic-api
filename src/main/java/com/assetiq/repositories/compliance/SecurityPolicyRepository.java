package com.assetiq.repositories.compliance;

import com.assetiq.models.Organisation;
import com.assetiq.models.compliance.SecurityPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SecurityPolicyRepository extends JpaRepository<SecurityPolicy, UUID> {

    List<SecurityPolicy> findByOrganisationAndDeletedAtIsNull(Organisation organisation);

    Optional<SecurityPolicy> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);
}
