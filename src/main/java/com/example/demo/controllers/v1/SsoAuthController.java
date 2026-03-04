package com.example.demo.controllers.v1;

import com.example.demo.enums.UserStatus;
import com.example.demo.models.OrgSsoConfig;
import com.example.demo.models.Role;
import com.example.demo.models.User;
import com.example.demo.repositories.OrgSsoConfigRepository;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.repositories.RoleRepository;
import com.example.demo.repositories.UserRepository;
import com.example.demo.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SSO authentication endpoints: OAuth2 and SAML login flows.
 *
 * These endpoints are stateless (no Spring session). After a successful
 * SSO assertion/callback the server issues a JWT in exactly the same
 * format as the regular /auth/login endpoint.
 */
@Tag(name = "SSO Auth", description = "OAuth2 and SAML SSO login flows")
@RestController
@RequestMapping("/api/v1/auth/sso")
public class SsoAuthController {

    private final OrgSsoConfigRepository ssoConfigRepository;
    private final OrganisationRepository organisationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtUtil jwtUtil;

    public SsoAuthController(OrgSsoConfigRepository ssoConfigRepository,
            OrganisationRepository organisationRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            JwtUtil jwtUtil) {
        this.ssoConfigRepository = ssoConfigRepository;
        this.organisationRepository = organisationRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.jwtUtil = jwtUtil;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // OAuth2 Flow
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Step 1 – Return the IdP authorization URL for the given organisation.
     *
     * GET /api/v1/auth/sso/oauth2/authorize?orgId={orgId}
     *
     * Response:
     * {
     * "authorizationUrl":
     * "https://accounts.google.com/o/oauth2/auth?client_id=...&redirect_uri=...&scope=...&response_type=code&state={orgId}"
     * }
     *
     * The client should redirect the end-user's browser to authorizationUrl.
     * The IdP will redirect back to the redirectUri with ?code=...&state={orgId}.
     */
    @Operation(summary = "Get OAuth2 authorization URL for an organisation")
    @GetMapping("/oauth2/authorize")
    public ResponseEntity<?> oauth2Authorize(@RequestParam UUID orgId) {
        OrgSsoConfig cfg = requireEnabledSso(orgId);

        if (cfg.getClientId() == null || cfg.getIssuerUri() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error",
                            "OAuth2 configuration is incomplete. Set clientId, issuerUri, and redirectUri via the SSO config API."));
        }

        // Derive the authorization_endpoint from standard OIDC discovery or known
        // providers
        String authEndpoint = resolveAuthorizationEndpoint(cfg);

        String scopes = cfg.getScopes() != null ? cfg.getScopes() : "openid email profile";
        String redirectUri = cfg.getRedirectUri() != null ? cfg.getRedirectUri() : "";

        try {
            String authUrl = authEndpoint
                    + "?client_id=" + URLEncoder.encode(cfg.getClientId(), StandardCharsets.UTF_8)
                    + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                    + "&scope=" + URLEncoder.encode(scopes, StandardCharsets.UTF_8)
                    + "&response_type=code"
                    + "&state=" + orgId.toString(); // carry orgId through the flow

            return ResponseEntity.ok(Map.of("authorizationUrl", authUrl));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to build authorization URL: " + e.getMessage()));
        }
    }

    /**
     * Step 2 – OAuth2 callback: exchange authorization code for user info, upsert
     * user, return JWT.
     *
     * GET /api/v1/auth/sso/oauth2/callback?code={code}&state={orgId}
     *
     * Response: same JWT structure as /api/v1/auth/login
     */
    @Operation(summary = "OAuth2 callback – exchange code for JWT")
    @GetMapping("/oauth2/callback")
    @Transactional
    public ResponseEntity<?> oauth2Callback(
            @RequestParam String code,
            @RequestParam(name = "state", required = false) String state) {

        if (state == null || state.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing state parameter (expected orgId)"));
        }

        UUID orgId;
        try {
            orgId = UUID.fromString(state);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid state parameter"));
        }

        OrgSsoConfig cfg = requireEnabledSso(orgId);

        if (cfg.getClientId() == null || cfg.getClientSecret() == null || cfg.getIssuerUri() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "OAuth2 configuration is incomplete."));
        }

        // Exchange code for tokens and retrieve user info
        Map<String, String> userInfo;
        try {
            String tokenEndpoint = resolveTokenEndpoint(cfg);
            String redirectUri = cfg.getRedirectUri() != null ? cfg.getRedirectUri() : "";
            Map<String, String> tokens = exchangeCodeForTokens(tokenEndpoint, code, cfg.getClientId(),
                    cfg.getClientSecret(), redirectUri);

            String accessToken = tokens.get("access_token");
            String idToken = tokens.get("id_token");

            // Use id_token (JWT) payload if available, else call userinfo endpoint
            if (idToken != null) {
                userInfo = parseIdTokenPayload(idToken);
            } else {
                userInfo = fetchUserInfo(cfg.getIssuerUri() + "/userinfo", accessToken);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Failed to exchange OAuth2 code: " + e.getMessage()));
        }

        String email = userInfo.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "IdP did not return an email claim"));
        }

        // Upsert user and issue JWT
        User user = upsertSsoUser(orgId, email,
                userInfo.getOrDefault("given_name", ""),
                userInfo.getOrDefault("family_name", ""));

        return ResponseEntity.ok(buildTokenResponse(user));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SAML Flow
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Step 1 – Return the SAML AuthnRequest redirect URL for the given
     * organisation.
     *
     * GET /api/v1/auth/sso/saml/initiate?orgId={orgId}
     *
     * Response:
     * {
     * "redirectUrl": "https://idp.example.com/sso/saml?SAMLRequest=..."
     * }
     *
     * The client redirects the browser to redirectUrl.
     * The IdP will POST a SAMLResponse to the ACS URL.
     */
    @Operation(summary = "Initiate SAML SSO login for an organisation")
    @GetMapping("/saml/initiate")
    public ResponseEntity<?> samlInitiate(@RequestParam UUID orgId) {
        OrgSsoConfig cfg = requireEnabledSso(orgId);

        if (cfg.getIdpMetadataUrl() == null || cfg.getSpEntityId() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error",
                            "SAML configuration is incomplete. Set idpMetadataUrl and spEntityId via the SSO config API."));
        }

        try {
            // Fetch IdP SSO URL from metadata
            String idpSsoUrl = fetchSsoUrlFromMetadata(cfg.getIdpMetadataUrl());

            // Build a minimal SAML AuthnRequest XML
            String acsUrl = cfg.getAssertionConsumerServiceUrl() != null
                    ? cfg.getAssertionConsumerServiceUrl()
                    : ""; // admin should configure this

            String authnRequestXml = buildSamlAuthnRequest(cfg.getSpEntityId(), acsUrl, idpSsoUrl, orgId);

            // Deflate-compress and Base64-encode (SAML redirect binding)
            String samlRequest = deflateThenBase64(authnRequestXml);

            String redirectUrl = idpSsoUrl
                    + "?SAMLRequest=" + URLEncoder.encode(samlRequest, StandardCharsets.UTF_8)
                    + "&RelayState=" + URLEncoder.encode(orgId.toString(), StandardCharsets.UTF_8);

            return ResponseEntity.ok(Map.of("redirectUrl", redirectUrl));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Failed to build SAML request: " + e.getMessage()));
        }
    }

    /**
     * Step 2 – SAML Assertion Consumer Service (ACS).
     *
     * POST /api/v1/auth/sso/saml/acs
     * Form body: SAMLResponse={base64}&RelayState={orgId}
     *
     * Parses the SAML response, extracts NameID (email), upserts user, returns JWT.
     */
    @Operation(summary = "SAML ACS endpoint – process assertion and issue JWT")
    @PostMapping(value = "/saml/acs", consumes = { "application/x-www-form-urlencoded" })
    @Transactional
    public ResponseEntity<?> samlAcs(
            @RequestParam("SAMLResponse") String samlResponseB64,
            @RequestParam(value = "RelayState", required = false) String relayState) {

        UUID orgId;
        try {
            orgId = UUID.fromString(relayState);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid RelayState (expected orgId)"));
        }

        requireEnabledSso(orgId);

        // Decode and parse the SAML response XML for NameID
        Map<String, String> assertionAttrs;
        try {
            byte[] decoded = Base64.getDecoder().decode(samlResponseB64);
            String xml = new String(decoded, StandardCharsets.UTF_8);
            assertionAttrs = parseSamlResponseAttributes(xml);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to parse SAML response: " + e.getMessage()));
        }

        String email = assertionAttrs.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "SAML assertion did not contain an email attribute or NameID"));
        }

        User user = upsertSsoUser(orgId, email,
                assertionAttrs.getOrDefault("firstName", ""),
                assertionAttrs.getOrDefault("lastName", ""));
        return ResponseEntity.ok(buildTokenResponse(user));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Shared helpers
    // ══════════════════════════════════════════════════════════════════════════

    private OrgSsoConfig requireEnabledSso(UUID orgId) {
        return ssoConfigRepository.findByOrganisationIdAndEnabledTrue(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "SSO is not enabled for this organisation. An admin must configure and enable it first."));
    }

    @Transactional
    private User upsertSsoUser(UUID orgId, String email, String firstName, String lastName) {
        var org = organisationRepository.findByIdAndDeletedAtIsNull(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organisation not found"));

        return userRepository.findByEmailAndOrganisationId(email, orgId)
                .map(existing -> {
                    existing.setLastLoginAt(Instant.now());
                    return userRepository.save(existing);
                })
                .orElseGet(() -> {
                    // First SSO login → create user with default USER role
                    Role defaultRole = roleRepository.findByNameAndOrganisationId("USER", orgId)
                            .orElse(null);
                    User u = new User();
                    u.setEmail(email);
                    u.setFirstName(firstName.isBlank() ? email.split("@")[0] : firstName);
                    u.setLastName(lastName.isBlank() ? "" : lastName);
                    u.setPasswordHash("SSO_USER_NO_PASSWORD");
                    u.setStatus(UserStatus.ACTIVE);
                    u.setOrganisation(org);
                    u.setRole(defaultRole);
                    u.setLastLoginAt(Instant.now());
                    return userRepository.save(u);
                });
    }

    private Map<String, Object> buildTokenResponse(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        claims.put("firstName", user.getFirstName());
        claims.put("lastName", user.getLastName());
        if (user.getRole() != null) {
            String roleName = user.getRole().getName();
            claims.put("role", roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName);
            claims.put("permissions", user.getRole().getPermissions());
        }
        if (user.getOrganisation() != null) {
            claims.put("organisationId", user.getOrganisation().getId().toString());
        }
        String token = jwtUtil.generateToken(user.getEmail(), claims, 1000L * 60 * 60 * 24);
        return Map.of(
                "token", token,
                "user", Map.of(
                        "id", user.getId(),
                        "email", user.getEmail(),
                        "firstName", user.getFirstName(),
                        "lastName", user.getLastName(),
                        "role", user.getRole() != null ? user.getRole().getName() : "NONE"),
                "expiresIn", 86400,
                "loginMethod", "SSO");
    }

    // ── OAuth2 helpers ────────────────────────────────────────────────────────

    private String resolveAuthorizationEndpoint(OrgSsoConfig cfg) {
        return switch (cfg.getProvider()) {
            case GOOGLE -> "https://accounts.google.com/o/oauth2/v2/auth";
            case AZURE_AD -> cfg.getIssuerUri() + "/oauth2/v2.0/authorize";
            case GITHUB -> "https://github.com/login/oauth/authorize";
            default -> cfg.getIssuerUri() + "/authorize";
        };
    }

    private String resolveTokenEndpoint(OrgSsoConfig cfg) {
        return switch (cfg.getProvider()) {
            case GOOGLE -> "https://oauth2.googleapis.com/token";
            case AZURE_AD -> cfg.getIssuerUri() + "/oauth2/v2.0/token";
            case GITHUB -> "https://github.com/login/oauth/access_token";
            default -> cfg.getIssuerUri() + "/token";
        };
    }

    /**
     * Exchange authorization code for tokens via HTTP POST to the token endpoint.
     * Returns a map containing at least "access_token" and optionally "id_token".
     */
    private Map<String, String> exchangeCodeForTokens(String tokenEndpoint, String code,
            String clientId, String clientSecret,
            String redirectUri) throws Exception {
        String body = "grant_type=authorization_code"
                + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);

        URL url = URI.create(tokenEndpoint).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Accept", "application/json");
        conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

        int status = conn.getResponseCode();
        try (java.io.InputStream is = status == 200 ? conn.getInputStream() : conn.getErrorStream()) {
            String json = new BufferedReader(new InputStreamReader(is)).lines()
                    .collect(java.util.stream.Collectors.joining());
            conn.disconnect();

            if (status != 200) {
                throw new RuntimeException("Token endpoint returned " + status + ": " + json);
            }

            return parseJsonToMap(json);
        }
    }

    /** Parse the id_token JWT payload (second segment, base64-decoded JSON). */
    private Map<String, String> parseIdTokenPayload(String idToken) {
        String[] parts = idToken.split("\\.");
        if (parts.length < 2)
            throw new IllegalArgumentException("Invalid id_token");
        String payload = new String(Base64.getUrlDecoder().decode(padBase64(parts[1])), StandardCharsets.UTF_8);
        return parseJsonToMap(payload);
    }

    /** Call the OIDC userinfo endpoint with an access token. */
    private Map<String, String> fetchUserInfo(String userinfoEndpoint, String accessToken) throws Exception {
        URL url = URI.create(userinfoEndpoint).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Accept", "application/json");
        try (java.io.InputStream is = conn.getInputStream()) {
            String json = new BufferedReader(new InputStreamReader(is)).lines()
                    .collect(java.util.stream.Collectors.joining());
            conn.disconnect();
            return parseJsonToMap(json);
        }
    }

    // ── SAML helpers ──────────────────────────────────────────────────────────

    /**
     * Fetch the IdP SSO URL (SingleSignOnService with HTTP-Redirect binding) from
     * the metadata XML.
     */
    private String fetchSsoUrlFromMetadata(String metadataUrl) throws Exception {
        URL url = URI.create(metadataUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Accept", "application/xml, text/xml");
        String xml;
        try (java.io.InputStream is = conn.getInputStream()) {
            xml = new BufferedReader(new InputStreamReader(is)).lines()
                    .collect(java.util.stream.Collectors.joining());
        }
        conn.disconnect();

        // Extract Location attribute of SingleSignOnService with HTTP-Redirect binding
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "SingleSignOnService[^>]*Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect\"[^>]*Location=\"([^\"]+)\"",
                java.util.regex.Pattern.DOTALL | java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(xml);
        if (m.find())
            return m.group(1);

        // Fallback: try POST binding
        p = java.util.regex.Pattern.compile("Location=\"(https://[^\"]+)\"");
        m = p.matcher(xml);
        if (m.find())
            return m.group(1);

        throw new RuntimeException("Could not find SingleSignOnService URL in IdP metadata");
    }

    /** Build a minimal SAML 2.0 AuthnRequest XML document. */
    private String buildSamlAuthnRequest(String spEntityId, String acsUrl, String idpSsoUrl, UUID orgId) {
        String id = "_" + UUID.randomUUID().toString().replace("-", "");
        String issueInstant = java.time.format.DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<samlp:AuthnRequest "
                + "    xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
                + "    xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
                + "    ID=\"" + id + "\" "
                + "    Version=\"2.0\" "
                + "    IssueInstant=\"" + issueInstant + "\" "
                + "    Destination=\"" + idpSsoUrl + "\" "
                + "    AssertionConsumerServiceURL=\"" + acsUrl + "\" "
                + "    ProtocolBinding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\">"
                + "  <saml:Issuer>" + spEntityId + "</saml:Issuer>"
                + "  <samlp:NameIDPolicy Format=\"urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress\" AllowCreate=\"true\"/>"
                + "</samlp:AuthnRequest>";
    }

    /**
     * DEFLATE-compress then Base64-encode an XML string (SAML redirect binding).
     */
    private String deflateThenBase64(String xml) throws java.io.IOException {
        byte[] xmlBytes = xml.getBytes(StandardCharsets.UTF_8);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.util.zip.DeflaterOutputStream deflater = new java.util.zip.DeflaterOutputStream(baos,
                new java.util.zip.Deflater(java.util.zip.Deflater.DEFAULT_COMPRESSION, true));
        deflater.write(xmlBytes);
        deflater.finish();
        deflater.flush();
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    /**
     * Parse a SAML Response XML and extract NameID and attribute values.
     * Supports both NameID as email and AttributeStatement email/name attributes.
     */
    private Map<String, String> parseSamlResponseAttributes(String xml) {
        Map<String, String> attrs = new HashMap<>();

        // 1) Try NameID
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<(?:saml(?:p)?:)?NameID[^>]*>([^<]+)<")
                .matcher(xml);
        if (m.find()) {
            String nameId = m.group(1).trim();
            if (nameId.contains("@")) {
                attrs.put("email", nameId);
            }
        }

        // 2) Attribute statements – look for email, givenName, surname
        java.util.regex.Pattern attrPattern = java.util.regex.Pattern.compile(
                "<(?:saml(?:p)?:)?Attribute[^>]+Name=\"([^\"]+)\"[^>]*>\\s*<(?:saml(?:p)?:)?AttributeValue[^>]*>([^<]+)<",
                java.util.regex.Pattern.DOTALL);
        m = attrPattern.matcher(xml);
        while (m.find()) {
            String name = m.group(1).toLowerCase();
            String value = m.group(2).trim();
            if (name.contains("email"))
                attrs.put("email", value);
            else if (name.contains("givenname") || name.contains("firstname") || name.contains("given_name"))
                attrs.put("firstName", value);
            else if (name.contains("surname") || name.contains("lastname") || name.contains("family_name"))
                attrs.put("lastName", value);
        }

        return attrs;
    }

    // ── JSON / utility helpers ────────────────────────────────────────────────

    /**
     * Very lightweight JSON-to-map parser for flat string key-values (no nested
     * objects).
     */
    private Map<String, String> parseJsonToMap(String json) {
        Map<String, String> result = new HashMap<>();
        // Match "key":"value" or "key":value patterns
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]+)\"\\s*:\\s*(?:\"([^\"]*)\"|([^,}\\]]+))")
                .matcher(json);
        while (m.find()) {
            String key = m.group(1);
            String value = m.group(2) != null ? m.group(2) : m.group(3);
            if (value != null)
                result.put(key, value.trim());
        }
        return result;
    }

    private String padBase64(String s) {
        while (s.length() % 4 != 0)
            s += "=";
        return s;
    }
}
