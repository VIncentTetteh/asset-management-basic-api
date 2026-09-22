package com.assetiq.services.impl;

import com.assetiq.dto.OrgSsoConfigDto;
import com.assetiq.dto.SsoDomainStatusDto;
import com.assetiq.enums.SsoProvider;
import com.assetiq.models.OrgSsoConfig;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.OrgSsoConfigRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.security.SecretCryptoService;
import com.assetiq.security.sso.DnsTxtResolver;
import com.assetiq.security.sso.EmailDomains;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * A domain has to be proved before SSO discovery will route on it. Without this,
 * any tenant could claim any domain — a competitor's, or gmail.com — and take
 * over the login of everyone who typed an address there.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SSO email-domain claims must be verified")
class SsoDomainVerificationTest {

    @Mock OrgSsoConfigRepository ssoConfigRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock SecretCryptoService secretCryptoService;
    @Mock DnsTxtResolver dnsTxtResolver;

    private SsoConfigServiceImpl service;
    private Organisation org;

    @BeforeEach
    void setUp() {
        service = new SsoConfigServiceImpl(ssoConfigRepository, organisationRepository,
                secretCryptoService, dnsTxtResolver);
        org = new Organisation();
        org.setId(UUID.randomUUID());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
        when(organisationRepository.save(any(Organisation.class))).thenAnswer(inv -> inv.getArgument(0));
        OrgSsoConfig config = new OrgSsoConfig();
        config.setOrganisation(org);
        when(ssoConfigRepository.findByOrganisationId(org.getId())).thenReturn(Optional.of(config));
        when(ssoConfigRepository.save(any(OrgSsoConfig.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void claim(String domain) {
        OrgSsoConfigDto dto = new OrgSsoConfigDto();
        dto.setProvider(SsoProvider.OKTA);
        dto.setEmailDomain(domain);
        service.saveOAuth2Config(org.getId(), dto, false);
    }

    @Test
    void claimingADomainStoresItUnverifiedWithAToken() {
        claim("acme.com");

        assertThat(org.getEmailDomain()).isEqualTo("acme.com");
        assertThat(org.getEmailDomainVerifiedAt()).isNull();
        SsoDomainStatusDto status = service.getDomainStatus(org.getId());
        assertThat(status.verified()).isFalse();
        assertThat(status.txtRecordValue()).startsWith("assetiq-verify=");
    }

    @Test
    void theTxtRecordVerifiesTheClaim() {
        claim("acme.com");
        String expected = service.getDomainStatus(org.getId()).txtRecordValue();
        when(dnsTxtResolver.txtRecords("acme.com")).thenReturn(List.of("v=spf1 -all", expected));

        SsoDomainStatusDto status = service.verifyDomain(org.getId());

        assertThat(status.verified()).isTrue();
        assertThat(org.getEmailDomainVerifiedAt()).isNotNull();
        // The token is spent once it has been used.
        assertThat(status.txtRecordValue()).isNull();
    }

    @Test
    void anotherOrganisationsTokenDoesNotVerifyTheClaim() {
        claim("acme.com");
        when(dnsTxtResolver.txtRecords("acme.com")).thenReturn(List.of("assetiq-verify=someoneelsestoken"));

        assertThatThrownBy(() -> service.verifyDomain(org.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TXT record");
        assertThat(org.getEmailDomainVerifiedAt()).isNull();
    }

    @Test
    void noRecordAtAllLeavesTheDomainUnverified() {
        claim("acme.com");
        when(dnsTxtResolver.txtRecords("acme.com")).thenReturn(List.of());

        assertThatThrownBy(() -> service.verifyDomain(org.getId())).isInstanceOf(IllegalStateException.class);
        assertThat(org.getEmailDomainVerifiedAt()).isNull();
    }

    @Test
    void changingTheDomainRevokesTheVerification() {
        claim("acme.com");
        String record = service.getDomainStatus(org.getId()).txtRecordValue();
        when(dnsTxtResolver.txtRecords("acme.com")).thenReturn(List.of(record));
        service.verifyDomain(org.getId());
        assertThat(org.getEmailDomainVerifiedAt()).isNotNull();

        claim("other-company.com");

        assertThat(org.getEmailDomainVerifiedAt()).isNull();
        assertThat(service.getDomainStatus(org.getId()).txtRecordValue()).startsWith("assetiq-verify=");
    }

    @Test
    void publicProvidersAreNeverClaimable() {
        for (String provider : new String[] {"gmail.com", "outlook.com", "yahoo.com", "icloud.com", "proton.me"}) {
            assertThat(EmailDomains.isPublicProvider(provider)).as(provider).isTrue();
        }
        claim("gmail.com");

        assertThatThrownBy(() -> service.verifyDomain(org.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("public email provider");
        assertThatThrownBy(() -> service.approveDomain(org.getId()))
                .isInstanceOf(IllegalStateException.class);
        // Even with a verification stamp forced on, the status refuses to route it.
        org.setEmailDomainVerifiedAt(java.time.Instant.now());
        assertThat(service.getDomainStatus(org.getId()).verified()).isFalse();
    }

    @Test
    void anOperatorCanApproveWhenDnsIsUnavailable() {
        claim("acme.com");

        SsoDomainStatusDto status = service.approveDomain(org.getId());

        assertThat(status.verified()).isTrue();
        assertThat(org.getEmailDomainVerifiedAt()).isNotNull();
    }

    @Test
    void verifyingWithNoDomainSetIsRefused() {
        assertThatThrownBy(() -> service.verifyDomain(org.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email domain");
    }
}
