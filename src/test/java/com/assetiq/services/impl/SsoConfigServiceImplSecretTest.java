package com.assetiq.services.impl;

import com.assetiq.dto.OrgSsoConfigDto;
import com.assetiq.enums.SsoProvider;
import com.assetiq.models.OrgSsoConfig;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.OrgSsoConfigRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.security.SecretCryptoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SsoConfigServiceImplSecretTest {

    @Mock OrgSsoConfigRepository ssoConfigRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock SecretCryptoService secretCryptoService;

    @Test
    void blankSecretKeepsTheStoredOne() {
        SsoConfigServiceImpl service = new SsoConfigServiceImpl(ssoConfigRepository, organisationRepository, secretCryptoService);
        UUID orgId = UUID.randomUUID();
        Organisation org = new Organisation();
        org.setId(orgId);
        OrgSsoConfig existing = new OrgSsoConfig();
        existing.setOrganisation(org);
        existing.setClientSecret("enc(real)");
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));
        when(ssoConfigRepository.findByOrganisationId(orgId)).thenReturn(Optional.of(existing));
        when(ssoConfigRepository.save(any(OrgSsoConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        OrgSsoConfigDto dto = new OrgSsoConfigDto();
        dto.setProvider(SsoProvider.OKTA);
        dto.setClientId("client");
        dto.setClientSecret("");
        service.saveOAuth2Config(orgId, dto);

        assertThat(existing.getClientSecret()).isEqualTo("enc(real)");
        verify(secretCryptoService, never()).encrypt(anyString());
    }
}
