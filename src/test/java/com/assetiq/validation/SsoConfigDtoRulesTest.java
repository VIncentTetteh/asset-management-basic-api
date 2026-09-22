package com.assetiq.validation;

import com.assetiq.dto.OrgSsoConfigDto;
import com.assetiq.enums.SsoProvider;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** SSO configuration: the email domain is a DNS name and the issuer an https URL. */
class SsoConfigDtoRulesTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> invalidFields(OrgSsoConfigDto dto) {
        return VALIDATOR.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString).collect(Collectors.toSet());
    }

    private static OrgSsoConfigDto dto() {
        OrgSsoConfigDto dto = new OrgSsoConfigDto();
        dto.setProvider(SsoProvider.OKTA);
        return dto;
    }

    @ParameterizedTest
    @ValueSource(strings = {"company.com", "mail.company.co.uk", "xn--bcher-kva.example", " acme.io ", ""})
    void acceptedEmailDomains(String domain) {
        OrgSsoConfigDto dto = dto();
        dto.setEmailDomain(domain);
        assertThat(invalidFields(dto)).doesNotContain("emailDomain");
    }

    @ParameterizedTest
    @ValueSource(strings = {"company", "@company.com", "user@company.com", "https://company.com",
            "company.com/path", "-bad.com", "bad-.com", "com.123", "a..b.com"})
    void refusedEmailDomains(String domain) {
        OrgSsoConfigDto dto = dto();
        dto.setEmailDomain(domain);
        assertThat(invalidFields(dto)).contains("emailDomain");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://login.company.com", "https://dev-123.okta.com/oauth2/default"})
    void acceptedIssuers(String issuer) {
        OrgSsoConfigDto dto = dto();
        dto.setIssuerUri(issuer);
        assertThat(invalidFields(dto)).doesNotContain("issuerUri");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://login.company.com", "login.company.com", "javascript:alert(1)", "https://"})
    void refusedIssuers(String issuer) {
        OrgSsoConfigDto dto = dto();
        dto.setIssuerUri(issuer);
        assertThat(invalidFields(dto)).contains("issuerUri");
    }
}
