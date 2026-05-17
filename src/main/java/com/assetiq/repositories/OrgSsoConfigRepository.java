package com.assetiq.repositories;

import com.assetiq.models.OrgSsoConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrgSsoConfigRepository extends JpaRepository<OrgSsoConfig, UUID> {

    Optional<OrgSsoConfig> findByOrganisationId(UUID organisationId);

    Optional<OrgSsoConfig> findByOrganisationIdAndEnabledTrue(UUID organisationId);

    /**
     * Returns all enabled SSO configs with their organisations eagerly fetched.
     * Used by {@link com.assetiq.config.Saml2OAuth2SecurityConfig} during bean
     * initialisation (outside a JPA transaction) to avoid LazyInitializationException.
     */
    @Query("SELECT c FROM OrgSsoConfig c JOIN FETCH c.organisation WHERE c.enabled = true")
    List<OrgSsoConfig> findAllEnabledWithOrganisation();

    @Query("SELECT c FROM OrgSsoConfig c JOIN FETCH c.organisation o " +
           "WHERE o.emailDomain = :domain AND c.enabled = true AND o.deletedAt IS NULL")
    Optional<OrgSsoConfig> findEnabledByOrganisationEmailDomain(@Param("domain") String domain);
}
