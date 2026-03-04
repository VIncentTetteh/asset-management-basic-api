package com.example.demo.dto;

import com.example.demo.enums.SsoProvider;
import lombok.Data;

import jakarta.validation.constraints.NotNull;

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

    private String clientId;

    /**
     * Write-only: set to provide a new secret; "********" on read.
     */
    private String clientSecret;

    private String issuerUri;

    private String scopes;

    private String redirectUri;

    // ── SAML fields ───────────────────────────────────────────────────────────

    private String idpMetadataUrl;

    private String spEntityId;

    private String assertionConsumerServiceUrl;
}
