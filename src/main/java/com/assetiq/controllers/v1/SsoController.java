package com.assetiq.controllers.v1;

import com.assetiq.dto.SsoDiscoverResponse;
import com.assetiq.enums.SsoProvider;
import com.assetiq.enums.UserStatus;
import com.assetiq.models.OrgSsoConfig;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import com.assetiq.repositories.OrgSsoConfigRepository;
import com.assetiq.repositories.RoleRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.security.JwtUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSO (Single Sign-On) authentication endpoints.
 *
 * <p>Supports two flows:
 * <ol>
 *   <li><b>OAuth2 / OIDC</b> — covers Google, Azure AD (OIDC), Okta, GitHub, and any
 *       provider configured with {@code clientId} + {@code issuerUri} in {@link OrgSsoConfig}.
 *       Uses Authorization Code flow with CSRF protection via one-time state and a
 *       nonce bound to the validated ID token.</li>
 *   <li><b>SAML 2.0</b> — initiation redirects the browser to the IdP single-sign-on URL
 *       derived from the IdP metadata; the ACS endpoint receives the assertion POST.</li>
 * </ol>
 *
 * <p>All flows terminate by issuing the application's own JWT so the rest of the stack
 * (stateless JWT auth, permission cache, tenant filter) remains unchanged.
 *
 * <h3>URL structure</h3>
 * <pre>
 * GET  /api/v1/auth/sso/initiate?orgId=&amp;provider=GOOGLE     → redirect to IdP
 * GET  /api/v1/auth/sso/callback?code=&amp;state=               → code exchange → JWT
 * POST /api/v1/auth/sso/saml/acs?orgId=                      → SAML ACS (assertion)
 * GET  /api/v1/auth/sso/config/{orgId}                       → SSO config info (public)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/auth/sso")
public class SsoController {

    private static final Logger log = LoggerFactory.getLogger(SsoController.class);

    /** State tokens expire after 10 minutes. */
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    /** Mobile/desktop exchange codes are intentionally short-lived and one-time use. */
    private static final Duration EXCHANGE_CODE_TTL = Duration.ofMinutes(2);

    private final OrgSsoConfigRepository ssoConfigRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;
    private final Map<String, StoredSsoState> inMemoryStates = new ConcurrentHashMap<>();
    private final Map<String, StoredSsoExchange> inMemoryExchangeCodes = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.jwt.expiration:86400000}")
    private long jwtExpirationMillis;

    @Value("${app.email.base-url:http://localhost:3000}")
    private String appBaseUrl;

    @Value("${app.sso.allowed-exchange-redirect-prefixes:assetiq://,eam://,exp://,http://localhost,http://127.0.0.1}")
    private String allowedExchangeRedirectPrefixes;

    @Value("${app.auth.cookie-secure:false}")
    private boolean authCookieSecure;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public SsoController(OrgSsoConfigRepository ssoConfigRepository,
                         UserRepository userRepository,
                         RoleRepository roleRepository,
                         JwtUtil jwtUtil,
                         PasswordEncoder passwordEncoder,
                         ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> redisProvider) {
        this.ssoConfigRepository = ssoConfigRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.redis = redisProvider.getIfAvailable();
    }

    // ── GET /api/v1/auth/sso/config/{orgId} ─────────────────────────────────

    /**
     * Returns whether SSO is enabled for an organisation and which provider type is active.
     * Intended to be called by the login page before showing the "Sign in with SSO" button.
     */
    @GetMapping("/config/{orgId}")
    public ResponseEntity<Map<String, Object>> getSsoConfig(@PathVariable UUID orgId) {
        Optional<OrgSsoConfig> cfg = ssoConfigRepository.findByOrganisationIdAndEnabledTrue(orgId);
        if (cfg.isEmpty()) {
            return ResponseEntity.ok(Map.of("ssoEnabled", false));
        }
        OrgSsoConfig c = cfg.get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ssoEnabled", true);
        result.put("provider", c.getProvider().name());
        result.put("isSaml", c.getProvider() == SsoProvider.SAML);
        return ResponseEntity.ok(result);
    }

    // ── GET /api/v1/auth/sso/discover ──────────────────────────────────────

    /**
     * Discovers whether an email domain is associated with an SSO-enabled organisation.
     * Returns identical {@code ssoEnabled: false} for unknown domains, disabled configs,
     * and malformed inputs — preventing email-domain enumeration.
     */
    @GetMapping("/discover")
    public ResponseEntity<SsoDiscoverResponse> discover(@RequestParam String email) {
        if (email == null || !email.contains("@")) {
            return ResponseEntity.ok(new SsoDiscoverResponse(false, null, null));
        }
        String domain = email.substring(email.lastIndexOf('@') + 1).trim().toLowerCase();
        if (domain.isEmpty()) {
            return ResponseEntity.ok(new SsoDiscoverResponse(false, null, null));
        }
        return ssoConfigRepository
                .findEnabledByOrganisationEmailDomain(domain)
                .map(c -> ResponseEntity.ok(new SsoDiscoverResponse(
                        true,
                        c.getOrganisation().getId().toString(),
                        c.getProvider().name())))
                .orElse(ResponseEntity.ok(new SsoDiscoverResponse(false, null, null)));
    }

    // ── GET /api/v1/auth/sso/initiate ───────────────────────────────────────

    /**
     * Initiates the SSO flow by redirecting the browser to the IdP authorization endpoint.
     *
     * @param orgId    the organisation UUID (tells us which IdP config to use)
     * @param provider optional provider override (e.g. GOOGLE, AZURE_AD); if absent the
     *                 org's configured provider is used
     */
    @GetMapping("/initiate")
    public void initiate(@RequestParam UUID orgId,
                         @RequestParam(required = false) String provider,
                         @RequestParam(required = false) String exchangeRedirectUri,
                         HttpServletResponse response) throws IOException {

        OrgSsoConfig cfg = ssoConfigRepository.findByOrganisationIdAndEnabledTrue(orgId)
                .orElse(null);
        if (cfg == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "SSO is not enabled for this organisation");
            return;
        }

        SsoProvider ssoProvider = provider != null ? parseSsoProvider(provider) : cfg.getProvider();

        if (ssoProvider == SsoProvider.SAML) {
            initiateSaml(cfg, response);
            return;
        }

        // OAuth2 / OIDC flow
        initiateOAuth2(cfg, orgId, exchangeRedirectUri, response);
    }

    // ── POST /api/v1/auth/sso/exchange ──────────────────────────────────────

    @PostMapping("/exchange")
    public ResponseEntity<?> exchange(@RequestBody Map<String, String> body) {
        String code = body != null ? body.get("code") : null;
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "code is required"));
        }

        StoredSsoExchange exchange = consumeExchangeCode(code);
        if (exchange == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "SSO exchange code is invalid or expired"));
        }

        Optional<User> user = userRepository.findById(exchange.userId());
        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "SSO user was not found"));
        }

        return ResponseEntity.ok(Map.of(
                "token", exchange.token(),
                "user", userSummary(user.get()),
                "expiresIn", jwtExpirationMillis / 1000));
    }

    // ── GET /api/v1/auth/sso/callback ───────────────────────────────────────

    /**
     * OAuth2 Authorization Code callback.
     * Exchanges the {@code code} for tokens, fetches the OIDC userinfo,
     * provisions/updates the user, and issues an AssetIQ JWT.
     */
    @GetMapping("/callback")
    public void callback(@RequestParam(required = false) String code,
                         @RequestParam(required = false) String state,
                         @RequestParam(required = false) String error,
                         HttpServletResponse servletResponse) throws IOException {

        if (error != null || code == null || state == null) {
            log.warn("[SSO] Callback with error='{}' code={} state={}", error, code != null, state != null);
            servletResponse.sendRedirect(appBaseUrl + "/login?error=sso_failed");
            return;
        }

        // Validate state and recover orgId
        StoredSsoState storedState = consumeState(state);
        if (storedState == null) {
            log.warn("[SSO] State token not found or expired for state={}", state);
            servletResponse.sendRedirect(appBaseUrl + "/login?error=sso_state_expired");
            return;
        }

        UUID orgId = storedState.orgId();
        OrgSsoConfig cfg = ssoConfigRepository.findByOrganisationIdAndEnabledTrue(orgId)
                .orElse(null);
        if (cfg == null) {
            servletResponse.sendRedirect(appBaseUrl + "/login?error=sso_config_missing");
            return;
        }

        try {
            // Exchange authorization code for tokens
            Map<String, String> tokenResponse = exchangeCodeForTokens(cfg, code);
            String idToken = tokenResponse.get("id_token");
            String accessToken = tokenResponse.get("access_token");

            // Fetch userinfo from the OIDC userinfo endpoint
            Map<String, Object> userInfo = fetchUserInfo(cfg, accessToken, idToken, storedState.nonce());
            String email = (String) userInfo.get("email");
            if (email == null || email.isBlank()) {
                log.warn("[SSO] No email in OIDC userinfo for org {}", orgId);
                servletResponse.sendRedirect(appBaseUrl + "/login?error=sso_no_email");
                return;
            }

            // Provision or update the user
            User user = provisionUser(email, userInfo, orgId, cfg);

            // Issue JWT
            String token = issueJwt(user);

            log.info("[SSO] OAuth2 login succeeded for {} in org {}", email, orgId);
            if (storedState.exchangeRedirectUri() != null) {
                String exchangeCode = UUID.randomUUID().toString().replace("-", "");
                storeExchangeCode(exchangeCode, user.getId(), token);
                servletResponse.sendRedirect(appendQuery(storedState.exchangeRedirectUri(),
                        "code=" + enc(exchangeCode) + "&sso=success"));
                return;
            }

            setAuthCookie(servletResponse, token, jwtExpirationMillis / 1000);
            servletResponse.sendRedirect(appBaseUrl + "/dashboard?sso=success");

        } catch (Exception ex) {
            log.error("[SSO] Callback error for org {}: {}", orgId, ex.getMessage(), ex);
            servletResponse.sendRedirect(appBaseUrl + "/login?error=sso_exchange_failed");
        }
    }

    // ── POST /api/v1/auth/sso/saml/acs ─────────────────────────────────────

    /**
     * SAML 2.0 Assertion Consumer Service (ACS) endpoint.
     *
     * <p>Spring Security's SAML2 library is wired in {@link com.assetiq.config.Saml2OAuth2SecurityConfig}
     * to handle signature verification and assertion parsing.  This endpoint receives the
     * already-validated principal, extracts the email attribute, and issues an AssetIQ JWT.
     *
     * <p>If Spring Security SAML2 is not enabled (missing IdP metadata), returns HTTP 501.
     */
    @PostMapping("/saml/acs")
    public ResponseEntity<?> samlAcs(
            @RequestParam UUID orgId,
            org.springframework.security.core.Authentication authentication,
            HttpServletResponse servletResponse) {

        if (authentication == null) {
            // Spring Security SAML2 filter chain should have rejected unauthenticated requests;
            // this branch is a safety net.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "SAML assertion not validated"));
        }

        try {
            // Extract email from the SAML2 principal name (typically the NameID)
            String email = authentication.getName();
            OrgSsoConfig cfg = ssoConfigRepository.findByOrganisationIdAndEnabledTrue(orgId)
                    .orElseThrow(() -> new IllegalStateException("SSO not configured for org " + orgId));

            User user = provisionUser(email, Map.of("email", email), orgId, cfg);
            String token = issueJwt(user);
            setAuthCookie(servletResponse, token, jwtExpirationMillis / 1000);

            log.info("[SSO] SAML login succeeded for {} in org {}", email, orgId);
            return ResponseEntity.ok(Map.of("token", token, "redirectUrl", appBaseUrl + "/dashboard"));

        } catch (Exception ex) {
            log.error("[SSO] SAML ACS error for org {}: {}", orgId, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "SAML authentication failed"));
        }
    }

    // ── OAuth2 helpers ────────────────────────────────────────────────────────

    private void storeState(String state, UUID orgId, String nonce, String exchangeRedirectUri) {
        StoredSsoState storedState = new StoredSsoState(orgId, nonce, exchangeRedirectUri, Instant.now().plus(STATE_TTL));
        if (redis != null) {
            redis.opsForValue().set("sso:state:" + state, storedState.serialize(), STATE_TTL);
            return;
        }
        inMemoryStates.put(state, storedState);
    }

    private StoredSsoState consumeState(String state) {
        if (redis != null) {
            String stored = redis.opsForValue().get("sso:state:" + state);
            redis.delete("sso:state:" + state);
            return StoredSsoState.deserialize(stored);
        }

        StoredSsoState stored = inMemoryStates.remove(state);
        if (stored == null || stored.expiresAt().isBefore(Instant.now())) {
            return null;
        }
        return stored;
    }

    private void initiateOAuth2(OrgSsoConfig cfg, UUID orgId, String exchangeRedirectUri, HttpServletResponse response)
            throws IOException {

        if (cfg.getClientId() == null || cfg.getIssuerUri() == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "OAuth2 SSO not fully configured for this organisation");
            return;
        }

        // Discover the authorization endpoint from the OIDC discovery document
        String authEndpoint = discoverAuthorizationEndpoint(cfg.getIssuerUri());
        if (authEndpoint == null) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Could not reach IdP discovery endpoint");
            return;
        }

        // Generate a CSRF-protecting state token and nonce for ID-token binding.
        String state = UUID.randomUUID().toString().replace("-", "");
        String nonce = UUID.randomUUID().toString().replace("-", "");
        storeState(state, orgId, nonce, normalizeExchangeRedirectUri(exchangeRedirectUri));

        String scopes = cfg.getScopes() != null ? cfg.getScopes() : "openid email profile";
        String redirectUri = cfg.getRedirectUri() != null
                ? cfg.getRedirectUri()
                : appBaseUrl.replaceAll("/+$", "") + "/api/v1/auth/sso/callback";

        String authUrl = authEndpoint
                + "?response_type=code"
                + "&client_id=" + enc(cfg.getClientId())
                + "&redirect_uri=" + enc(redirectUri)
                + "&scope=" + enc(scopes)
                + "&state=" + enc(state)
                + "&nonce=" + enc(nonce)
                + "&prompt=select_account";

        response.sendRedirect(authUrl);
    }

    private void storeExchangeCode(String code, UUID userId, String token) {
        StoredSsoExchange exchange = new StoredSsoExchange(userId, token, Instant.now().plus(EXCHANGE_CODE_TTL));
        if (redis != null) {
            redis.opsForValue().set("sso:exchange:" + code, exchange.serialize(), EXCHANGE_CODE_TTL);
            return;
        }
        inMemoryExchangeCodes.put(code, exchange);
    }

    private StoredSsoExchange consumeExchangeCode(String code) {
        if (redis != null) {
            String stored = redis.opsForValue().get("sso:exchange:" + code);
            redis.delete("sso:exchange:" + code);
            return StoredSsoExchange.deserialize(stored);
        }

        StoredSsoExchange exchange = inMemoryExchangeCodes.remove(code);
        if (exchange == null || exchange.expiresAt().isBefore(Instant.now())) {
            return null;
        }
        return exchange;
    }

    private String normalizeExchangeRedirectUri(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            return null;
        }
        String trimmed = redirectUri.trim();
        for (String prefix : allowedExchangeRedirectPrefixes.split(",")) {
            String allowed = prefix.trim();
            if (!allowed.isBlank() && trimmed.startsWith(allowed)) {
                return trimmed;
            }
        }
        return null;
    }

    private String appendQuery(String uri, String query) {
        return uri + (uri.contains("?") ? "&" : "?") + query;
    }

    private void initiateSaml(OrgSsoConfig cfg, HttpServletResponse response) throws IOException {
        if (cfg.getIdpMetadataUrl() == null || cfg.getAssertionConsumerServiceUrl() == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "SAML SSO not fully configured for this organisation");
            return;
        }
        // Spring Security's SAML2 filter chain (configured in Saml2OAuth2SecurityConfig)
        // handles the actual AuthnRequest construction and redirect.
        // We redirect to the managed SAML initiation URL which Spring Security intercepts.
        String spEntityId = cfg.getSpEntityId() != null
                ? cfg.getSpEntityId()
                : cfg.getOrganisation().getId().toString();
        response.sendRedirect("/saml2/authenticate/" + enc(spEntityId));
    }

    /**
     * Fetches the OIDC discovery document and extracts the authorization endpoint.
     * Caches nothing — called once per initiation request.
     */
    private String discoverAuthorizationEndpoint(String issuerUri) {
        try {
            String discoveryUrl = issuerUri.replaceAll("/+$", "") + "/.well-known/openid-configuration";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(discoveryUrl))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;

            // Minimal JSON extraction — authorization_endpoint is always a top-level key
            String body = resp.body();
            return extractJsonString(body, "authorization_endpoint");

        } catch (Exception ex) {
            log.warn("[SSO] Discovery failed for {}: {}", issuerUri, ex.getMessage());
            return null;
        }
    }

    /**
     * Exchanges the authorization code for an access token and ID token at the
     * token endpoint (discovered from OIDC discovery document).
     */
    private Map<String, String> exchangeCodeForTokens(OrgSsoConfig cfg, String code) throws Exception {
        String tokenEndpoint = discoverTokenEndpoint(cfg.getIssuerUri());
        if (tokenEndpoint == null) {
            throw new IllegalStateException("Could not discover token endpoint for " + cfg.getIssuerUri());
        }

        String redirectUri = cfg.getRedirectUri() != null
                ? cfg.getRedirectUri()
                : appBaseUrl.replaceAll("/+$", "") + "/api/v1/auth/sso/callback";

        String body = "grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(redirectUri)
                + "&client_id=" + enc(cfg.getClientId())
                + "&client_secret=" + enc(cfg.getClientSecret() != null ? cfg.getClientSecret() : "");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("Token exchange failed (HTTP " + resp.statusCode() + "): " + resp.body());
        }
        return parseJsonToStringMap(resp.body());
    }

    private String discoverTokenEndpoint(String issuerUri) {
        try {
            String discoveryUrl = issuerUri.replaceAll("/+$", "") + "/.well-known/openid-configuration";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(discoveryUrl))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 ? extractJsonString(resp.body(), "token_endpoint") : null;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Validates the OIDC ID token and then enriches with userinfo when available.
     * User provisioning only sees claims after issuer, signature, audience,
     * expiry, and nonce have been checked.
     */
    private Map<String, Object> fetchUserInfo(OrgSsoConfig cfg, String accessToken, String idToken, String expectedNonce)
            throws Exception {
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalStateException("OIDC provider did not return an id_token");
        }

        Map<String, Object> verifiedClaims = validateIdTokenClaims(cfg, idToken, expectedNonce);
        Map<String, Object> identityClaims = new LinkedHashMap<>(verifiedClaims);
        String userInfoEndpoint = discoverUserInfoEndpoint(cfg.getIssuerUri());

        if (userInfoEndpoint != null && accessToken != null) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(userInfoEndpoint))
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", "Bearer " + accessToken)
                        .GET()
                        .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    Map<String, Object> userInfo = parseJsonToObjectMap(resp.body());
                    identityClaims.putAll(userInfo);
                    return identityClaims;
                }
            } catch (Exception ex) {
                log.warn("[SSO] UserInfo fetch failed; using verified ID token claims: {}", ex.getMessage());
            }
        }

        return identityClaims;
    }

    private String discoverUserInfoEndpoint(String issuerUri) {
        try {
            String discoveryUrl = issuerUri.replaceAll("/+$", "") + "/.well-known/openid-configuration";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(discoveryUrl))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 ? extractJsonString(resp.body(), "userinfo_endpoint") : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> validateIdTokenClaims(OrgSsoConfig cfg, String idToken, String expectedNonce) {
        Map<String, Object> discovery = discoverOidcConfiguration(cfg.getIssuerUri());
        String jwksUri = stringClaim(discovery, "jwks_uri");
        String issuer = Optional.ofNullable(stringClaim(discovery, "issuer"))
                .filter(s -> !s.isBlank())
                .orElse(cfg.getIssuerUri());

        if (jwksUri == null || jwksUri.isBlank()) {
            throw new IllegalStateException("OIDC discovery document did not include jwks_uri");
        }

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            if (jwt.getAudience().contains(cfg.getClientId())) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token", "ID token audience does not include configured client_id", null));
        };
        OAuth2TokenValidator<Jwt> nonceValidator = jwt -> {
            Object nonce = jwt.getClaims().get("nonce");
            if (expectedNonce != null && expectedNonce.equals(nonce)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token", "ID token nonce did not match SSO state", null));
        };

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                audienceValidator,
                nonceValidator));

        try {
            Jwt jwt = decoder.decode(idToken);
            return jwt.getClaims();
        } catch (JwtException ex) {
            throw new IllegalStateException("ID token validation failed: " + ex.getMessage(), ex);
        }
    }

    private Map<String, Object> discoverOidcConfiguration(String issuerUri) {
        try {
            String discoveryUrl = issuerUri.replaceAll("/+$", "") + "/.well-known/openid-configuration";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(discoveryUrl))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("OIDC discovery failed with HTTP " + resp.statusCode());
            }
            return parseJsonToObjectMap(resp.body());
        } catch (Exception ex) {
            throw new IllegalStateException("Could not discover OIDC configuration for " + issuerUri, ex);
        }
    }

    private String stringClaim(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        return value == null ? null : value.toString();
    }

    // ── User provisioning ────────────────────────────────────────────────────

    /**
     * Creates a new user or updates the existing one based on OIDC claims.
     * Assigns the default USER role on first creation.
     */
    private User provisionUser(String email, Map<String, Object> claims, UUID orgId, OrgSsoConfig cfg) {
        Organisation org = cfg.getOrganisation();

        Optional<User> existing = userRepository.findByEmailAndOrganisationId(email, orgId);
        User user = existing.orElseGet(User::new);

        if (user.getId() == null || !existing.isPresent()) {
            // New SSO user
            user.setEmail(email);
            user.setOrganisation(org);
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString())); // unusable random PW
            user.setStatus(UserStatus.ACTIVE);

            // Assign default USER role
            roleRepository.findByNameAndOrganisationId("USER", orgId)
                    .ifPresent(user::setRole);
        }

        // Update profile from OIDC claims (idempotent)
        String givenName  = (String) claims.getOrDefault("given_name",  claims.get("name"));
        String familyName = (String) claims.get("family_name");
        if (givenName  != null) user.setFirstName(givenName.split(" ")[0]);
        if (familyName != null) user.setLastName(familyName);
        if (user.getFirstName() == null || user.getFirstName().isBlank()) {
            user.setFirstName(email.split("@")[0]);
        }

        user.setLastLoginAt(Instant.now());
        return userRepository.save(user);
    }

    // ── JWT / Cookie helpers ─────────────────────────────────────────────────

    private String issueJwt(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        claims.put("firstName", user.getFirstName());
        claims.put("lastName", user.getLastName());
        claims.put("sso", true);
        if (user.getRole() != null) {
            String r = user.getRole().getName();
            claims.put("role", r.startsWith("ROLE_") ? r : "ROLE_" + r);
        }
        if (user.getOrganisation() != null) {
            claims.put("organisationId", user.getOrganisation().getId().toString());
        }
        return jwtUtil.generateToken(user.getEmail(), claims, jwtExpirationMillis);
    }

    private Map<String, Object> userSummary(User user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("email", user.getEmail());
        result.put("firstName", user.getFirstName());
        result.put("lastName", user.getLastName());
        result.put("role", user.getRole() != null ? user.getRole().getName() : "NONE");
        if (user.getOrganisation() != null) {
            result.put("organisationId", user.getOrganisation().getId());
        }
        return result;
    }

    private void setAuthCookie(HttpServletResponse response, String token, long maxAgeSec) {
        ResponseCookie cookie = ResponseCookie.from("access_token", token)
                .httpOnly(true).secure(authCookieSecure).sameSite("Lax").path("/api").maxAge(maxAgeSec).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    // ── Minimal JSON helpers (no Jackson dependency on this path) ────────────

    /** Naive regex-free JSON string-field extractor. Works for flat OIDC discovery docs. */
    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + search.length());
        if (colon < 0) return null;
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return null;
        int end = json.indexOf('"', start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end);
    }

    /** Very naive flat JSON → Map<String,String> parser (for token endpoint response). */
    private Map<String, String> parseJsonToStringMap(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {});
            raw.forEach((k, v) -> map.put(k, v != null ? v.toString() : null));
        } catch (Exception ignored) { /* best effort */ }
        return map;
    }

    private Map<String, Object> parseJsonToObjectMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s != null ? s : "", StandardCharsets.UTF_8);
    }

    private SsoProvider parseSsoProvider(String s) {
        try { return SsoProvider.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException ex) { return SsoProvider.GOOGLE; }
    }

    private record StoredSsoState(UUID orgId, String nonce, String exchangeRedirectUri, Instant expiresAt) {
        String serialize() {
            return orgId + "|" + nonce + "|" + b64(exchangeRedirectUri) + "|" + expiresAt.toEpochMilli();
        }

        static StoredSsoState deserialize(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String[] parts = value.split("\\|", 4);
            if (parts.length != 3 && parts.length != 4) {
                return null;
            }
            try {
                int expiresAtIndex = parts.length == 4 ? 3 : 2;
                StoredSsoState state = new StoredSsoState(
                        UUID.fromString(parts[0]),
                        parts[1],
                        parts.length == 4 ? unb64(parts[2]) : null,
                        Instant.ofEpochMilli(Long.parseLong(parts[expiresAtIndex])));
                return state.expiresAt().isBefore(Instant.now()) ? null : state;
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private record StoredSsoExchange(UUID userId, String token, Instant expiresAt) {
        String serialize() {
            return userId + "|" + b64(token) + "|" + expiresAt.toEpochMilli();
        }

        static StoredSsoExchange deserialize(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String[] parts = value.split("\\|", 3);
            if (parts.length != 3) {
                return null;
            }
            try {
                StoredSsoExchange exchange = new StoredSsoExchange(
                        UUID.fromString(parts[0]),
                        unb64(parts[1]),
                        Instant.ofEpochMilli(Long.parseLong(parts[2])));
                return exchange.expiresAt().isBefore(Instant.now()) ? null : exchange;
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private static String b64(String value) {
        if (value == null) {
            return "";
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String unb64(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
