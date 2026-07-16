package com.assetiq.config;

import com.assetiq.models.OrgSsoConfig;
import com.assetiq.repositories.OrgSsoConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;

import java.util.ArrayList;
import java.util.List;

/**
 * SAML 2.0 Service-Provider configuration.
 *
 * <p>Reads enabled SAML SSO configurations from the {@code org_sso_config} table and
 * registers a Spring Security {@link RelyingPartyRegistration} for each one.  Spring
 * Security then handles:
 * <ul>
 *   <li>Building and signing AuthnRequests</li>
 *   <li>Receiving, decrypting, and verifying SAML assertions at the ACS endpoint</li>
 *   <li>Populating a {@link org.springframework.security.core.Authentication} principal</li>
 * </ul>
 *
 * <p>The resulting authenticated principal is picked up by
 * {@link com.assetiq.controllers.v1.SsoController#samlAcs} which converts it to an
 * AssetIQ JWT and issues the HttpOnly cookie.
 *
 * <p>This bean is only created when {@code app.sso.saml.enabled=true} (default false),
 * preventing startup failures in deployments where no SAML IdP is configured.
 *
 * <h3>Per-org SP entity IDs</h3>
 * Each organisation gets its own registration ID (the org UUID string) so multiple
 * SAML IdPs can coexist in a multi-tenant deployment.  The ACS URL pattern is:
 * {@code /login/saml2/sso/{registrationId}}.
 */
@Configuration
public class Saml2OAuth2SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(Saml2OAuth2SecurityConfig.class);

    /**
     * Builds the SAML2 relying-party registration repository by scanning all enabled
     * SAML {@link OrgSsoConfig} rows.
     *
     * <p>Enabled only when {@code app.sso.saml.enabled=true} so the bean does not
     * attempt IdP metadata downloads during CI / local runs without a live IdP.
     *
     * <p>For production multi-tenant deployments consider replacing this
     * {@link InMemoryRelyingPartyRegistrationRepository} with a custom implementation
     * that delegates to the DB so newly-configured orgs pick up without a restart.
     */
    @Bean
    @ConditionalOnProperty(name = "app.sso.saml.enabled", havingValue = "true", matchIfMissing = false)
    public RelyingPartyRegistrationRepository relyingPartyRegistrationRepository(
            OrgSsoConfigRepository ssoConfigRepository) {

        List<RelyingPartyRegistration> registrations = new ArrayList<>();

        // Use the eager-fetch query to avoid LazyInitializationException outside a transaction
        List<OrgSsoConfig> samlConfigs = ssoConfigRepository.findAllEnabledWithOrganisation().stream()
                .filter(c -> c.getIdpMetadataUrl() != null && !c.getIdpMetadataUrl().isBlank())
                .filter(c -> c.getAssertionConsumerServiceUrl() != null)
                .toList();

        for (OrgSsoConfig cfg : samlConfigs) {
            String registrationId = cfg.getOrganisation().getId().toString();
            try {
                RelyingPartyRegistration registration = RelyingPartyRegistrations
                        .fromMetadataLocation(cfg.getIdpMetadataUrl())
                        .registrationId(registrationId)
                        .entityId(cfg.getSpEntityId() != null
                                ? cfg.getSpEntityId()
                                : "assetiq-sp-" + registrationId)
                        .assertionConsumerServiceLocation(cfg.getAssertionConsumerServiceUrl())
                        .build();

                registrations.add(registration);
                log.info("[SAML] Registered SP for org {} via IdP metadata: {}",
                        registrationId, cfg.getIdpMetadataUrl());

            } catch (Exception ex) {
                // Log and skip — one bad IdP metadata URL should not prevent other orgs from booting
                log.error("[SAML] Failed to load IdP metadata for org {}: {}",
                        registrationId, ex.getMessage());
            }
        }

        if (registrations.isEmpty()) {
            log.info("[SAML] No valid SAML configurations found — registering empty repository");
        }

        return new InMemoryRelyingPartyRegistrationRepository(registrations);
    }
}
