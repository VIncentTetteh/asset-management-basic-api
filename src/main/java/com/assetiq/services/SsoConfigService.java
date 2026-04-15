package com.assetiq.services;

import com.assetiq.dto.OrgSsoConfigDto;

import java.util.UUID;

public interface SsoConfigService {

    OrgSsoConfigDto getByOrgId(UUID orgId);

    OrgSsoConfigDto saveOAuth2Config(UUID orgId, OrgSsoConfigDto dto);

    OrgSsoConfigDto saveSamlConfig(UUID orgId, OrgSsoConfigDto dto);

    OrgSsoConfigDto setEnabled(UUID orgId, boolean enabled);
}
