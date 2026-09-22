package com.assetiq.services.impl;

import com.assetiq.dto.OrgSsoConfigDto;
import com.assetiq.config.CachingConfig;
import com.assetiq.enums.SsoProvider;
import com.assetiq.models.Organisation;
import com.assetiq.models.OrgSsoConfig;
import com.assetiq.repositories.OrgSsoConfigRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.SsoConfigService;
import com.assetiq.security.SecretCryptoService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class SsoConfigServiceImpl implements SsoConfigService {

    private final OrgSsoConfigRepository ssoConfigRepository;
    private final OrganisationRepository organisationRepository;
    private final SecretCryptoService secretCryptoService;

    public SsoConfigServiceImpl(OrgSsoConfigRepository ssoConfigRepository,
            OrganisationRepository organisationRepository,
            SecretCryptoService secretCryptoService) {
        this.ssoConfigRepository = ssoConfigRepository;
        this.organisationRepository = organisationRepository;
        this.secretCryptoService = secretCryptoService;
    }

    @Override
    @Cacheable(value = CachingConfig.CacheNames.SSO_CONFIG_BY_ORG, key = "#orgId.toString()", unless = "#result == null")
    public OrgSsoConfigDto getByOrgId(UUID orgId) {
        return ssoConfigRepository.findByOrganisationId(orgId)
                .map(this::toDto)
                .orElse(null);
    }

    @Override
    @Transactional
    @CacheEvict(value = CachingConfig.CacheNames.SSO_CONFIG_BY_ORG, key = "#orgId.toString()")
    public OrgSsoConfigDto saveOAuth2Config(UUID orgId, OrgSsoConfigDto dto, boolean replaceExisting) {
        Organisation org = requireOrg(orgId);
        SsoProvider provider = dto.getProvider() != null ? dto.getProvider() : SsoProvider.GOOGLE;
        if (provider == SsoProvider.SAML) {
            throw new IllegalArgumentException("Use the SAML settings to configure SAML single sign-on");
        }
        OrgSsoConfig config = loadOrCreate(org);
        if (isSaml(config)) {
            requireReplaceConsent(replaceExisting, "SAML", "OAuth2");
            clearSamlFields(config);
            config.setEnabled(false);
        }
        config.setProvider(provider);
        config.setClientId(dto.getClientId());
        // Only update the secret when a real value is supplied: the form sends ""
        // for "leave blank to keep existing", which used to overwrite the stored
        // secret with an empty one and break sign-in.
        if (dto.getClientSecret() != null && !dto.getClientSecret().isBlank()
                && !dto.getClientSecret().startsWith("****")) {
            config.setClientSecret(secretCryptoService.encrypt(dto.getClientSecret()));
        }
        config.setIssuerUri(dto.getIssuerUri());
        if (dto.getScopes() != null)
            config.setScopes(dto.getScopes());
        config.setRedirectUri(dto.getRedirectUri());
        persistEmailDomain(org, dto.getEmailDomain());

        return toDto(ssoConfigRepository.save(config));
    }

    @Override
    @Transactional
    @CacheEvict(value = CachingConfig.CacheNames.SSO_CONFIG_BY_ORG, key = "#orgId.toString()")
    public OrgSsoConfigDto saveSamlConfig(UUID orgId, OrgSsoConfigDto dto, boolean replaceExisting) {
        Organisation org = requireOrg(orgId);
        OrgSsoConfig config = loadOrCreate(org);
        if (!isSaml(config) && hasOAuth2Settings(config)) {
            requireReplaceConsent(replaceExisting, "OAuth2", "SAML");
            clearOAuth2Fields(config);
            config.setEnabled(false);
        }
        config.setProvider(SsoProvider.SAML);
        config.setIdpMetadataUrl(dto.getIdpMetadataUrl());
        config.setSpEntityId(dto.getSpEntityId());
        config.setAssertionConsumerServiceUrl(dto.getAssertionConsumerServiceUrl());
        persistEmailDomain(org, dto.getEmailDomain());

        return toDto(ssoConfigRepository.save(config));
    }

    @Override
    @Transactional
    @CacheEvict(value = CachingConfig.CacheNames.SSO_CONFIG_BY_ORG, key = "#orgId.toString()")
    public OrgSsoConfigDto setEnabled(UUID orgId, boolean enabled) {
        requireOrg(orgId);
        OrgSsoConfig config = ssoConfigRepository.findByOrganisationId(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No SSO configuration found for this organisation. Configure SSO first."));
        config.setEnabled(enabled);
        return toDto(ssoConfigRepository.save(config));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private OrgSsoConfig loadOrCreate(Organisation org) {
        return ssoConfigRepository.findByOrganisationId(org.getId())
                .orElseGet(() -> {
                    OrgSsoConfig c = new OrgSsoConfig();
                    c.setOrganisation(org);
                    return c;
                });
    }

    private static boolean isSaml(OrgSsoConfig config) {
        return config.getProvider() == SsoProvider.SAML;
    }

    private static boolean hasOAuth2Settings(OrgSsoConfig config) {
        return notBlank(config.getClientId()) || notBlank(config.getClientSecret())
                || notBlank(config.getIssuerUri());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * The config row serves both types, so saving one silently overwrote the other.
     * Replacing is allowed only when the caller says so explicitly.
     */
    private static void requireReplaceConsent(boolean replaceExisting, String existingType, String newType) {
        if (!replaceExisting) {
            throw new IllegalStateException("This organisation already uses " + existingType
                    + " single sign-on. Saving " + newType + " settings replaces it and switches SSO off"
                    + " until you enable it again. Confirm the replacement to continue.");
        }
    }

    private static void clearSamlFields(OrgSsoConfig config) {
        config.setIdpMetadataUrl(null);
        config.setSpEntityId(null);
        config.setAssertionConsumerServiceUrl(null);
    }

    private static void clearOAuth2Fields(OrgSsoConfig config) {
        config.setClientId(null);
        config.setClientSecret(null);
        config.setIssuerUri(null);
        config.setRedirectUri(null);
    }

    private Organisation requireOrg(UUID orgId) {
        return organisationRepository.findByIdAndDeletedAtIsNull(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organisation not found"));
    }

    private void persistEmailDomain(Organisation org, String raw) {
        if (raw == null) return;
        String domain = raw.trim().toLowerCase();
        org.setEmailDomain(domain.isEmpty() ? null : domain);
        organisationRepository.save(org);
    }

    private OrgSsoConfigDto toDto(OrgSsoConfig config) {
        OrgSsoConfigDto dto = new OrgSsoConfigDto();
        dto.setId(config.getId());
        dto.setOrganisationId(config.getOrganisation().getId());
        dto.setProvider(config.getProvider());
        dto.setEnabled(config.isEnabled());
        dto.setClientId(config.getClientId());
        // Mask secret on read
        dto.setClientSecret(config.getClientSecret() != null ? "********" : null);
        dto.setIssuerUri(config.getIssuerUri());
        dto.setScopes(config.getScopes());
        dto.setRedirectUri(config.getRedirectUri());
        dto.setIdpMetadataUrl(config.getIdpMetadataUrl());
        dto.setSpEntityId(config.getSpEntityId());
        dto.setAssertionConsumerServiceUrl(config.getAssertionConsumerServiceUrl());
        dto.setEmailDomain(config.getOrganisation().getEmailDomain());
        return dto;
    }
}
