package com.assetiq.services;

import com.assetiq.dto.OrgSsoConfigDto;
import com.assetiq.dto.SsoDomainStatusDto;

import java.util.UUID;

public interface SsoConfigService {

    OrgSsoConfigDto getByOrgId(UUID orgId);

    /**
     * Saves OAuth2/OIDC settings. An organisation has one active SSO type: when SAML
     * is configured, this refuses with 409 unless {@code replaceExisting} is true, in
     * which case the SAML settings are cleared and SSO is switched off until re-enabled.
     */
    OrgSsoConfigDto saveOAuth2Config(UUID orgId, OrgSsoConfigDto dto, boolean replaceExisting);

    /** SAML counterpart of {@link #saveOAuth2Config}; replacing clears the OAuth2 settings. */
    OrgSsoConfigDto saveSamlConfig(UUID orgId, OrgSsoConfigDto dto, boolean replaceExisting);

    OrgSsoConfigDto setEnabled(UUID orgId, boolean enabled);

    /** The organisation's email domain, whether it is verified, and the TXT record to publish. */
    SsoDomainStatusDto getDomainStatus(UUID orgId);

    /**
     * Looks for {@code assetiq-verify=<token>} in the domain's TXT records and
     * marks it verified when it is there. Until a domain is verified, SSO
     * discovery never routes on it, so no tenant can claim another's domain.
     *
     * @throws IllegalStateException when no domain is set, or it is a public provider
     */
    SsoDomainStatusDto verifyDomain(UUID orgId);

    /**
     * Marks the domain verified on an operator's word, for deployments where the
     * server cannot make DNS lookups. Only ever reached through PlatformAdminGuard.
     */
    SsoDomainStatusDto approveDomain(UUID orgId);
}
