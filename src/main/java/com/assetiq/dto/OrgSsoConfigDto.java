package com.assetiq.dto;

import com.assetiq.validation.HttpUrl;
import jakarta.validation.constraints.Pattern;

import com.assetiq.enums.SsoProvider;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for reading/writing per-organisation SSO configuration.
 * clientSecret is write-only (masked on read).
 */
@Data
public class OrgSsoConfigDto {

    /** DB primary key – present only on responses */
    private java.util.UUID id;

    private java.util.UUID organisationId;

    @NotNull(message = "provider is required")
    private SsoProvider provider;

    private boolean enabled;

    // ── OAuth2 fields ─────────────────────────────────────────────────────────

    @Size(max = 255)
    private String clientId;

    /**
     * Write-only: set to provide a new secret; "********" on read.
     */
    private String clientSecret;

    /** OpenID Connect issuer / discovery base: an absolute https URL. */
    @Size(max = 255)
    @HttpUrl(httpsOnly = true, message = "must be an https:// URL")
    private String issuerUri;

    @Size(max = 255)
    private String scopes;

    @HttpUrl
    private String redirectUri;

    // ── SAML fields ───────────────────────────────────────────────────────────

    @HttpUrl
    private String idpMetadataUrl;

    @Size(max = 255)
    private String spEntityId;

    @HttpUrl
    private String assertionConsumerServiceUrl;

    /** Email domain for SSO auto-discovery (e.g. "company.com"). Stored on the Organisation. */
    @Size(max = 255)
    @Pattern(regexp = EMAIL_DOMAIN_PATTERN, message = "must be a domain such as company.com")
    private String emailDomain;

    /**
     * Blank, or a DNS name with at least two labels: letters, digits and inner
     * hyphens, 63 characters a label, ending in an alphabetic TLD. No scheme, "@" or path.
     */
    public static final String EMAIL_DOMAIN_PATTERN =
            "^\\s*$|^\\s*(?=.{1,253}\\s*$)([A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}\\s*$";
}
