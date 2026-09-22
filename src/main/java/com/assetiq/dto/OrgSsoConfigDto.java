package com.assetiq.dto;

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

    @Size(max = 255)
    private String issuerUri;

    @Size(max = 255)
    private String scopes;

    private String redirectUri;

    // ── SAML fields ───────────────────────────────────────────────────────────

    private String idpMetadataUrl;

    @Size(max = 255)
    private String spEntityId;

    private String assertionConsumerServiceUrl;

    /** Email domain for SSO auto-discovery (e.g. "company.com"). Stored on the Organisation. */
    @Size(max = 255)
    private String emailDomain;
}
