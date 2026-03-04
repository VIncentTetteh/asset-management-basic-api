package com.example.demo.services;

import com.example.demo.dto.OrgSsoConfigDto;

import java.util.UUID;

public interface SsoConfigService {

    OrgSsoConfigDto getByOrgId(UUID orgId);

    OrgSsoConfigDto saveOAuth2Config(UUID orgId, OrgSsoConfigDto dto);

    OrgSsoConfigDto saveSamlConfig(UUID orgId, OrgSsoConfigDto dto);

    OrgSsoConfigDto setEnabled(UUID orgId, boolean enabled);
}
