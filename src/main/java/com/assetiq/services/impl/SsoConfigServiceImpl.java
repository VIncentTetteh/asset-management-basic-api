package com.assetiq.services.impl;

import com.assetiq.dto.OrgSsoConfigDto;
import com.assetiq.dto.SsoDomainStatusDto;
import com.assetiq.config.CachingConfig;
import com.assetiq.enums.SsoProvider;
import com.assetiq.models.Organisation;
import com.assetiq.models.OrgSsoConfig;
import com.assetiq.repositories.OrgSsoConfigRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.SsoConfigService;
import com.assetiq.security.SecretCryptoService;
import com.assetiq.security.sso.DnsTxtResolver;
import com.assetiq.security.sso.EmailDomains;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class SsoConfigServiceImpl implements SsoConfigService {

    private final OrgSsoConfigRepository ssoConfigRepository;
    private final OrganisationRepository organisationRepository;
    private final SecretCryptoService secretCryptoService;
    private final DnsTxtResolver dnsTxtResolver;

    /** The TXT record's prefix; the whole value is {@code assetiq-verify=<token>}. */
    static final String TXT_PREFIX = "assetiq-verify=";

    public SsoConfigServiceImpl(OrgSsoConfigRepository ssoConfigRepository,
            OrganisationRepository organisationRepository,
            SecretCryptoService secretCryptoService,
            DnsTxtResolver dnsTxtResolver) {
        this.ssoConfigRepository = ssoConfigRepository;
        this.organisationRepository = organisationRepository;
        this.secretCryptoService = secretCryptoService;
        this.dnsTxtResolver = dnsTxtResolver;
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

    /**
     * Stores the claimed domain. A new or changed domain starts unverified with a
     * fresh token: SSO discovery routes on a domain only once its owner has proved
     * the claim, so setting one here grants nothing by itself.
     */
    private void persistEmailDomain(Organisation org, String raw) {
        if (raw == null) return;
        String domain = EmailDomains.normalise(raw);
        String current = EmailDomains.normalise(org.getEmailDomain());
        if (java.util.Objects.equals(domain, current)) return;

        org.setEmailDomain(domain);
        org.setEmailDomainVerifiedAt(null);
        org.setEmailDomainToken(domain == null ? null : newToken());
        organisationRepository.save(org);
    }

    private static String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    @Transactional
    public SsoDomainStatusDto getDomainStatus(UUID orgId) {
        Organisation org = requireOrg(orgId);
        // A domain stored before verification existed (or before this call) has no
        // token yet; mint one so the settings screen has something to show.
        if (org.getEmailDomain() != null && org.getEmailDomainToken() == null
                && org.getEmailDomainVerifiedAt() == null) {
            org.setEmailDomainToken(newToken());
            organisationRepository.save(org);
        }
        return domainStatus(org);
    }

    @Override
    @Transactional
    @CacheEvict(value = CachingConfig.CacheNames.SSO_CONFIG_BY_ORG, key = "#orgId.toString()")
    public SsoDomainStatusDto verifyDomain(UUID orgId) {
        Organisation org = requireOrg(orgId);
        String domain = requireClaimableDomain(org);
        if (org.getEmailDomainVerifiedAt() != null) return domainStatus(org);

        String token = org.getEmailDomainToken();
        if (token == null) {
            org.setEmailDomainToken(token = newToken());
            organisationRepository.save(org);
        }
        String expected = TXT_PREFIX + token;
        boolean found = dnsTxtResolver.txtRecords(domain).stream()
                .map(r -> r == null ? "" : r.trim().toLowerCase(Locale.ROOT))
                .anyMatch(expected.toLowerCase(Locale.ROOT)::equals);
        if (!found) {
            throw new IllegalStateException("No \"" + expected + "\" TXT record found on " + domain
                    + ". Publish it in your DNS and try again (changes can take a few minutes).");
        }
        return markVerified(org);
    }

    @Override
    @Transactional
    @CacheEvict(value = CachingConfig.CacheNames.SSO_CONFIG_BY_ORG, key = "#orgId.toString()")
    public SsoDomainStatusDto approveDomain(UUID orgId) {
        Organisation org = requireOrg(orgId);
        requireClaimableDomain(org);
        return markVerified(org);
    }

    private SsoDomainStatusDto markVerified(Organisation org) {
        org.setEmailDomainVerifiedAt(Instant.now());
        org.setEmailDomainToken(null);
        organisationRepository.save(org);
        return domainStatus(org);
    }

    /** The domain, if there is one an organisation is allowed to own. */
    private String requireClaimableDomain(Organisation org) {
        String domain = EmailDomains.normalise(org.getEmailDomain());
        if (domain == null) {
            throw new IllegalStateException("Set an email domain in the SSO settings first");
        }
        if (EmailDomains.isPublicProvider(domain)) {
            throw new IllegalStateException(domain + " is a public email provider and cannot be "
                    + "claimed by an organisation. Use a domain your organisation owns.");
        }
        return domain;
    }

    private SsoDomainStatusDto domainStatus(Organisation org) {
        String domain = EmailDomains.normalise(org.getEmailDomain());
        boolean publicProvider = EmailDomains.isPublicProvider(domain);
        boolean verified = org.getEmailDomainVerifiedAt() != null && !publicProvider;
        String token = org.getEmailDomainToken();
        return new SsoDomainStatusDto(
                domain,
                verified,
                publicProvider,
                domain == null ? null : domain,
                verified || token == null ? null : TXT_PREFIX + token);
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
