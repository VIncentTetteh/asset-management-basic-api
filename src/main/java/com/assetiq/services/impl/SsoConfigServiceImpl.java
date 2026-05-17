package com.assetiq.services.impl;

import com.assetiq.dto.OrgSsoConfigDto;
import com.assetiq.config.CachingConfig;
import com.assetiq.enums.SsoProvider;
import com.assetiq.models.Organisation;
import com.assetiq.models.OrgSsoConfig;
import com.assetiq.repositories.OrgSsoConfigRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.SsoConfigService;
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

    public SsoConfigServiceImpl(OrgSsoConfigRepository ssoConfigRepository,
            OrganisationRepository organisationRepository) {
        this.ssoConfigRepository = ssoConfigRepository;
        this.organisationRepository = organisationRepository;
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
    public OrgSsoConfigDto saveOAuth2Config(UUID orgId, OrgSsoConfigDto dto) {
        Organisation org = requireOrg(orgId);
        OrgSsoConfig config = ssoConfigRepository.findByOrganisationId(orgId)
                .orElseGet(() -> {
                    OrgSsoConfig c = new OrgSsoConfig();
                    c.setOrganisation(org);
                    return c;
                });

        SsoProvider provider = dto.getProvider() != null ? dto.getProvider() : SsoProvider.GOOGLE;
        config.setProvider(provider);
        config.setClientId(dto.getClientId());
        // Only update secret if a non-masked value is supplied
        if (dto.getClientSecret() != null && !dto.getClientSecret().startsWith("****")) {
            config.setClientSecret(dto.getClientSecret());
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
    public OrgSsoConfigDto saveSamlConfig(UUID orgId, OrgSsoConfigDto dto) {
        Organisation org = requireOrg(orgId);
        OrgSsoConfig config = ssoConfigRepository.findByOrganisationId(orgId)
                .orElseGet(() -> {
                    OrgSsoConfig c = new OrgSsoConfig();
                    c.setOrganisation(org);
                    return c;
                });

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
