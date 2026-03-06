package com.example.demo.models;

import com.example.demo.enums.SsoProvider;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * Per-organisation SSO configuration.
 * Admins use the API to set clientId/clientSecret/issuerUri for OAuth2
 * or idpMetadataUrl/spEntityId for SAML, then toggle enabled=true.
 */
@Entity
@Setter
@Getter
@Table(name = "org_sso_config", uniqueConstraints = @UniqueConstraint(columnNames = "organisation_id", name = "uk_sso_org"))
public class OrgSsoConfig extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SsoProvider provider = SsoProvider.GOOGLE;

    @Column(nullable = false)
    private boolean enabled = false;

    // ── OAuth2 fields ─────────────────────────────────────────────────────────

    /** OAuth2 client ID registered with the IdP */
    @Column(name = "client_id")
    private String clientId;

    /** OAuth2 client secret – stored encrypted / kept server-side */
    @Column(name = "client_secret", columnDefinition = "TEXT")
    private String clientSecret;

    /** OIDC issuer URI, e.g. https://accounts.google.com */
    @Column(name = "issuer_uri")
    private String issuerUri;

    /** Space-separated scopes, e.g. "openid email profile" */
    @Column(name = "scopes")
    private String scopes = "openid email profile";

    /** OAuth2 redirect URI registered with IdP */
    @Column(name = "redirect_uri", columnDefinition = "TEXT")
    private String redirectUri;

    // ── SAML fields ───────────────────────────────────────────────────────────

    /** URL to the IdP XML metadata document */
    @Column(name = "idp_metadata_url", columnDefinition = "TEXT")
    private String idpMetadataUrl;

    /** This SP's entity ID (audience) */
    @Column(name = "sp_entity_id")
    private String spEntityId;

    /** Assertion Consumer Service URL (ACS) for this SP */
    @Column(name = "acs_url", columnDefinition = "TEXT")
    private String assertionConsumerServiceUrl;
}
