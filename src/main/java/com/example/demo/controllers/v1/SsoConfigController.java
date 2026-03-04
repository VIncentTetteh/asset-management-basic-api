package com.example.demo.controllers.v1;

import com.example.demo.dto.OrgSsoConfigDto;
import com.example.demo.services.SsoConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Admin API for managing per-organisation SSO (SAML / OAuth2) configuration.
 *
 * All endpoints require ORG_ADMIN or ADMIN authority.
 */
@Tag(name = "SSO Configuration", description = "Manage per-organisation SAML and OAuth2 SSO settings")
@RestController
@RequestMapping("/api/v1/organisations/{orgId}/sso")
public class SsoConfigController {

    private final SsoConfigService ssoConfigService;

    public SsoConfigController(SsoConfigService ssoConfigService) {
        this.ssoConfigService = ssoConfigService;
    }

    /**
     * GET /api/v1/organisations/{orgId}/sso
     * Returns the current SSO configuration for the organisation.
     * If none exists, returns 204 No Content.
     */
    @Operation(summary = "Get SSO config for an organisation")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN')")
    public ResponseEntity<?> getSsoConfig(@PathVariable UUID orgId) {
        OrgSsoConfigDto dto = ssoConfigService.getByOrgId(orgId);
        if (dto == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(dto);
    }

    /**
     * PUT /api/v1/organisations/{orgId}/sso/oauth2
     * Save or update OAuth2 settings (clientId, clientSecret, issuerUri, scopes,
     * redirectUri).
     * Also sets provider based on dto.provider (defaults to GOOGLE if omitted).
     */
    @Operation(summary = "Configure OAuth2 SSO for an organisation")
    @PutMapping("/oauth2")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN')")
    public ResponseEntity<OrgSsoConfigDto> saveOAuth2Config(
            @PathVariable UUID orgId,
            @Valid @RequestBody OrgSsoConfigDto dto) {
        return ResponseEntity.ok(ssoConfigService.saveOAuth2Config(orgId, dto));
    }

    /**
     * PUT /api/v1/organisations/{orgId}/sso/saml
     * Save or update SAML settings (idpMetadataUrl, spEntityId,
     * assertionConsumerServiceUrl).
     */
    @Operation(summary = "Configure SAML SSO for an organisation")
    @PutMapping("/saml")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN')")
    public ResponseEntity<OrgSsoConfigDto> saveSamlConfig(
            @PathVariable UUID orgId,
            @Valid @RequestBody OrgSsoConfigDto dto) {
        return ResponseEntity.ok(ssoConfigService.saveSamlConfig(orgId, dto));
    }

    /**
     * PATCH /api/v1/organisations/{orgId}/sso/toggle
     * Enable or disable SSO for the organisation.
     * Body: { "enabled": true | false }
     */
    @Operation(summary = "Enable or disable SSO for an organisation")
    @PatchMapping("/toggle")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN')")
    public ResponseEntity<OrgSsoConfigDto> toggleSso(
            @PathVariable UUID orgId,
            @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(ssoConfigService.setEnabled(orgId, enabled));
    }
}
