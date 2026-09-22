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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        service.saveOAuth2Config(orgId, dto, false);

        assertThat(existing.getClientSecret()).isEqualTo("enc(real)");
        verify(secretCryptoService, never()).encrypt(anyString());
    }

    private SsoConfigServiceImpl serviceWith(Organisation org, OrgSsoConfig existing) {
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
        when(ssoConfigRepository.findByOrganisationId(org.getId())).thenReturn(Optional.ofNullable(existing));
        when(ssoConfigRepository.save(any(OrgSsoConfig.class))).thenAnswer(inv -> inv.getArgument(0));
        return new SsoConfigServiceImpl(ssoConfigRepository, organisationRepository, secretCryptoService);
    }

    private static Organisation org() {
        Organisation org = new Organisation();
        org.setId(UUID.randomUUID());
        return org;
    }

    private static OrgSsoConfig samlConfig(Organisation org) {
        OrgSsoConfig c = new OrgSsoConfig();
        c.setOrganisation(org);
        c.setProvider(SsoProvider.SAML);
        c.setEnabled(true);
        c.setIdpMetadataUrl("https://idp.example.com/metadata");
        c.setSpEntityId("assetiq");
        return c;
    }

    private static OrgSsoConfig oauthConfig(Organisation org) {
        OrgSsoConfig c = new OrgSsoConfig();
        c.setOrganisation(org);
        c.setProvider(SsoProvider.OKTA);
        c.setEnabled(true);
        c.setClientId("client");
        c.setClientSecret("enc(secret)");
        c.setIssuerUri("https://okta.example.com");
        return c;
    }

    private static OrgSsoConfigDto oauthDto() {
        OrgSsoConfigDto dto = new OrgSsoConfigDto();
        dto.setProvider(SsoProvider.GOOGLE);
        dto.setClientId("new-client");
        return dto;
    }

    private static OrgSsoConfigDto samlDto() {
        OrgSsoConfigDto dto = new OrgSsoConfigDto();
        dto.setProvider(SsoProvider.SAML);
        dto.setIdpMetadataUrl("https://idp.example.com/new");
        return dto;
    }

    @Test
    void oauth2SaveRefusesToSilentlyReplaceSaml() {
        Organisation org = org();
        OrgSsoConfig existing = samlConfig(org);
        SsoConfigServiceImpl service = serviceWith(org, existing);

        assertThatThrownBy(() -> service.saveOAuth2Config(org.getId(), oauthDto(), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already uses SAML");
        assertThat(existing.getProvider()).isEqualTo(SsoProvider.SAML);
        assertThat(existing.getIdpMetadataUrl()).isNotNull();
        verify(ssoConfigRepository, never()).save(any());
    }

    @Test
    void oauth2ReplaceClearsSamlAndSwitchesSsoOff() {
        Organisation org = org();
        OrgSsoConfig existing = samlConfig(org);
        SsoConfigServiceImpl service = serviceWith(org, existing);

        service.saveOAuth2Config(org.getId(), oauthDto(), true);

        assertThat(existing.getProvider()).isEqualTo(SsoProvider.GOOGLE);
        assertThat(existing.getIdpMetadataUrl()).isNull();
        assertThat(existing.getSpEntityId()).isNull();
        assertThat(existing.isEnabled()).isFalse();
        assertThat(existing.getClientId()).isEqualTo("new-client");
    }

    @Test
    void samlSaveRefusesToSilentlyReplaceOAuth2() {
        Organisation org = org();
        OrgSsoConfig existing = oauthConfig(org);
        SsoConfigServiceImpl service = serviceWith(org, existing);

        assertThatThrownBy(() -> service.saveSamlConfig(org.getId(), samlDto(), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already uses OAuth2");
        assertThat(existing.getProvider()).isEqualTo(SsoProvider.OKTA);
    }

    @Test
    void samlReplaceClearsOAuth2SecretAndSwitchesSsoOff() {
        Organisation org = org();
        OrgSsoConfig existing = oauthConfig(org);
        SsoConfigServiceImpl service = serviceWith(org, existing);

        service.saveSamlConfig(org.getId(), samlDto(), true);

        assertThat(existing.getProvider()).isEqualTo(SsoProvider.SAML);
        assertThat(existing.getClientId()).isNull();
        assertThat(existing.getClientSecret()).isNull();
        assertThat(existing.isEnabled()).isFalse();
    }

    @Test
    void sameTypeSavesNeedNoConfirmation() {
        Organisation org = org();
        OrgSsoConfig existing = samlConfig(org);
        SsoConfigServiceImpl service = serviceWith(org, existing);

        service.saveSamlConfig(org.getId(), samlDto(), false);

        assertThat(existing.isEnabled()).isTrue();
        assertThat(existing.getIdpMetadataUrl()).isEqualTo("https://idp.example.com/new");
    }

    @Test
    void firstConfigurationNeedsNoConfirmation() {
        Organisation org = org();
        SsoConfigServiceImpl service = serviceWith(org, null);

        OrgSsoConfigDto saved = service.saveSamlConfig(org.getId(), samlDto(), false);

        assertThat(saved.getProvider()).isEqualTo(SsoProvider.SAML);
    }

    @Test
    void oauth2EndpointRejectsTheSamlProvider() {
        Organisation org = org();
        SsoConfigServiceImpl service = serviceWith(org, null);
        OrgSsoConfigDto dto = oauthDto();
        dto.setProvider(SsoProvider.SAML);

        assertThatThrownBy(() -> service.saveOAuth2Config(org.getId(), dto, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
