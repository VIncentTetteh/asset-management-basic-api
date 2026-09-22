package com.assetiq.services;

import com.assetiq.dto.OrgSsoConfigDto;

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
}
