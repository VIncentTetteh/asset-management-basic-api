package com.example.demo.repositories;

import com.example.demo.models.OrgSsoConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrgSsoConfigRepository extends JpaRepository<OrgSsoConfig, UUID> {

    Optional<OrgSsoConfig> findByOrganisationId(UUID organisationId);

    Optional<OrgSsoConfig> findByOrganisationIdAndEnabledTrue(UUID organisationId);
}
