package com.assetiq.controllers.v1;

import com.assetiq.exceptions.MfaCodeInvalidException;
import com.assetiq.exceptions.MfaEnrolmentRequiredException;
import com.assetiq.models.User;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.UserRepository;
import com.assetiq.security.JwtUtil;
import com.assetiq.services.RefreshSessionService;
import com.assetiq.services.SessionRevocationService;
import com.assetiq.security.SecretCryptoService;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;

import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TOTP-based Multi-Factor Authentication endpoints.
 *
 * Setup flow:
 *   1. POST /setup    → generates a TOTP secret + QR code (base64 PNG); user scans in authenticator app
 *   2. POST /verify   → confirms the first TOTP code and enables MFA on the account
 *   3. DELETE /disable → disables MFA after verifying a current TOTP code
 *
 * Login flow (after password verification):
 *   4. POST /challenge → exchange mfaChallengeToken + TOTP code for a full JWT
 *
 * Step-up (already signed in, before a @RequireFreshMfa action):
 *   5. POST /step-up   → TOTP code → access token re-issued with a fresh mfaAuthenticatedAt
 */
@RestController
@RequestMapping("/api/v1/mfa")
public class MfaController {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final RefreshSessionService refreshSessionService;
    private final SecretCryptoService secretCryptoService;
    private final SessionRevocationService sessionRevocationService;
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(
            new dev.samstevens.totp.code.DefaultCodeGenerator(HashingAlgorithm.SHA1),
            new SystemTimeProvider());

    @Value("${app.jwt.expiration:86400000}")
    private long jwtExpirationMillis;

    @Value("${app.auth.cookie-secure:true}")
    private boolean authCookieSecure;

    public MfaController(UserRepository userRepository, JwtUtil jwtUtil,
                         RefreshSessionService refreshSessionService,
                         SecretCryptoService secretCryptoService,
                         SessionRevocationService sessionRevocationService) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.refreshSessionService = refreshSessionService;
        this.secretCryptoService = secretCryptoService;
        this.sessionRevocationService = sessionRevocationService;
    }

    /**
     * Generates a new TOTP secret for the authenticated user and returns a base64-encoded QR PNG.
     * Does NOT enable MFA yet — the user must call /verify with a valid code first.
     *
     * POST /api/v1/mfa/setup
     */
    @PostMapping("/setup")
    public ResponseEntity<Map<String, String>> setup(Authentication auth) throws QrGenerationException {
        User user = resolveUser(auth);
        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            // Re-running setup would silently replace a live secret without proof of
            // the current one: a hijacked session could take over the second factor,
            // and the real user would be locked out. Disable (with a code) first.
            throw new IllegalStateException("MFA is already enabled. Disable it before setting up a new authenticator.");
        }

        String secret = secretGenerator.generate();
        user.setMfaSecret(secretCryptoService.encrypt(secret));
        userRepository.save(user);

        QrData qrData = new QrData.Builder()
                .label(user.getEmail())
                .secret(secret)
                .issuer("AssetManager")
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        byte[] qrImageBytes = qrGenerator.generate(qrData);
        String qrBase64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(qrImageBytes);

        return ResponseEntity.ok(Map.of(
                "secret", secret,
                "qrCodeImage", qrBase64,
                "message", "Scan the QR code in your authenticator app, then call POST /verify"
        ));
    }

    /**
     * Verifies a TOTP code and activates MFA for the user.
     *
     * POST /api/v1/mfa/verify
     * Body: { "code": "123456" }
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, String>> verify(Authentication auth,
                                                      @RequestBody Map<String, String> body) {
        User user = resolveUser(auth);

        if (user.getMfaSecret() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MFA setup not started. Call POST /setup first."));
        }

        String code = body.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing 'code' in request body."));
        }

        if (!isValidTotp(user, code)) {
            // Typed errorCode, not a bare 401 body: the web client must be able to
            // tell a mistyped code apart from an expired session (which signs out).
            throw new MfaCodeInvalidException("Invalid authenticator code.");
        }

        user.setMfaEnabled(true);
        sessionRevocationService.revokeAll(user);

        return ResponseEntity.ok(Map.of("message", "MFA enabled successfully. Sign in again to continue."));
    }

    /**
     * Disables MFA after confirming with a valid TOTP code.
     *
     * DELETE /api/v1/mfa/disable
     * Body: { "code": "123456" }
     */
    @DeleteMapping("/disable")
    public ResponseEntity<Map<String, String>> disable(Authentication auth,
                                                       @RequestBody Map<String, String> body) {
        User user = resolveUser(auth);

        if (!Boolean.TRUE.equals(user.getMfaEnabled())) {
            return ResponseEntity.badRequest().body(Map.of("error", "MFA is not enabled."));
        }

        String code = body.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing 'code' in request body."));
        }

        if (!isValidTotp(user, code)) {
            // Typed errorCode, not a bare 401 body: the web client must be able to
            // tell a mistyped code apart from an expired session (which signs out).
            throw new MfaCodeInvalidException("Invalid authenticator code.");
        }

        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        sessionRevocationService.revokeAll(user);

        return ResponseEntity.ok(Map.of("message", "MFA disabled successfully. All sessions were revoked."));
    }

    /**
     * Exchange a short-lived MFA challenge token (issued by POST /auth/login when
     * mfaEnabled=true) and a valid TOTP code for a full-access JWT.
     *
     * POST /api/v1/mfa/challenge
     * Body: { "mfaChallengeToken": "...", "code": "123456" }
     *
     * This endpoint is intentionally permit-all (no Bearer required) because the
     * caller is in the middle of the login flow and does not yet have a real token.
     */
    @PostMapping("/challenge")
    public ResponseEntity<?> challenge(@RequestBody Map<String, String> body,
                                       HttpServletResponse servletResponse) {
        String challengeToken = body.get("mfaChallengeToken");
        String code = body.get("code");

        if (challengeToken == null || challengeToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing mfaChallengeToken."));
        }
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing 'code' in request body."));
        }

        // Validate the challenge token
        Claims claims;
        try {
            claims = jwtUtil.parseToken(challengeToken);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired challenge token."));
        }

        // Ensure this is actually a challenge token and not a regular JWT
        Boolean isMfaChallenge = claims.get("mfaChallenge", Boolean.class);
        if (!Boolean.TRUE.equals(isMfaChallenge)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Provided token is not an MFA challenge token."));
        }

        // Look up the user
        String email = claims.getSubject();
        String orgIdStr = claims.get("organisationId", String.class);

        User user;
        try {
            if (orgIdStr != null && !orgIdStr.isBlank()) {
                user = userRepository.findByEmailAndOrganisationId(email, UUID.fromString(orgIdStr))
                        .orElseThrow(() -> new IllegalStateException("User not found"));
            } else {
                user = userRepository.findByEmail(email)
                        .orElseThrow(() -> new IllegalStateException("User not found"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not found."));
        }

        Number challengeSessionVersion = claims.get("sessionVersion", Number.class);
        if (challengeSessionVersion == null
                || challengeSessionVersion.longValue() != user.getSessionVersion()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "MFA challenge has been revoked."));
        }

        // Verify the TOTP code
        if (!isValidTotp(user, code)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid authenticator code."));
        }

        // Update last login timestamp
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Build and return the full JWT
        String token = buildAccessToken(user, Instant.now().getEpochSecond());
        RefreshSessionService.IssuedRefreshToken refresh = refreshSessionService.issue(user);
        setSessionCookies(servletResponse, token, refresh);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "refreshToken", refresh.token(),
                "tokenType", "Bearer",
                "user", Map.of(
                        "id", user.getId(),
                        "email", user.getEmail(),
                        "firstName", user.getFirstName(),
                        "lastName", user.getLastName(),
                        "role", user.getRole() != null ? user.getRole().getName() : "NONE"),
                "expiresIn", jwtExpirationMillis / 1000));
    }

    /**
     * Step-up re-authentication for an already signed-in user.
     *
     * <p>Verifies a current TOTP code through the same {@link #isValidTotp} path as
     * {@code /challenge} and re-issues the access token (cookie + body, for Bearer
     * clients) with a fresh {@code mfaAuthenticatedAt}, satisfying
     * {@code @RequireFreshMfa} for its max age.
     *
     * <p>Only the access token is replaced. The existing refresh session is kept: the
     * user has not started a new login, and minting a second refresh session per
     * approval would multiply live sessions. Because refresh deliberately drops
     * {@code mfaAuthenticatedAt}, step-up assurance still expires on schedule.
     *
     * <p>Rate limited with the auth tier ({@code /api/v1/mfa/**} in
     * {@code RateLimitingInterceptor}), same as {@code /challenge}.
     *
     * POST /api/v1/mfa/step-up
     * Body: { "code": "123456" }
     */
    @PostMapping("/step-up")
    public ResponseEntity<Map<String, Object>> stepUp(Authentication auth,
                                                      @RequestBody Map<String, String> body,
                                                      HttpServletResponse servletResponse) {
        User user = resolveUser(auth);
        if (!Boolean.TRUE.equals(user.getMfaEnabled()) || user.getMfaSecret() == null) {
            throw new MfaEnrolmentRequiredException(
                    "Two-factor authentication must be set up before performing this action");
        }

        String code = body != null ? body.get("code") : null;
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Missing 'code' in request body.");
        }
        if (!isValidTotp(user, code)) {
            throw new MfaCodeInvalidException("Invalid authenticator code.");
        }

        long mfaAuthenticatedAt = Instant.now().getEpochSecond();
        String token = buildAccessToken(user, mfaAuthenticatedAt);
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, accessCookie(token).toString());

        return ResponseEntity.ok(Map.of(
                "mfaAuthenticatedAt", mfaAuthenticatedAt,
                "token", token,
                "tokenType", "Bearer",
                "expiresIn", jwtExpirationMillis / 1000));
    }

    /** Single TOTP verification path shared by {@code /challenge} and {@code /step-up}. */
    private boolean isValidTotp(User user, String code) {
        return user.getMfaSecret() != null
                && codeVerifier.isValidCode(secretCryptoService.decrypt(user.getMfaSecret()), code);
    }

    /**
     * Builds a full-access JWT for an MFA-verified user. {@code mfaAuthenticatedAt}
     * is the short-lived proof used by {@code @RequireFreshMfa}; refresh deliberately
     * does not copy it, so step-up assurance cannot be extended silently.
     */
    private String buildAccessToken(User user, long mfaAuthenticatedAt) {
        Map<String, Object> fullClaims = new HashMap<>();
        fullClaims.put("email", user.getEmail());
        fullClaims.put("firstName", user.getFirstName());
        fullClaims.put("lastName", user.getLastName());
        fullClaims.put("sessionVersion", user.getSessionVersion());
        fullClaims.put("mfaAuthenticatedAt", mfaAuthenticatedAt);

        if (user.getRole() != null) {
            String roleName = user.getRole().getName();
            fullClaims.put("role", roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName);
            // Permissions intentionally excluded from the JWT (Phase 1 / B-6).
            // They are resolved live from the permission cache on every request.
        }
        if (user.getOrganisation() != null) {
            fullClaims.put("organisationId", user.getOrganisation().getId().toString());
        }
        if (user.getDepartment() != null) {
            fullClaims.put("departmentId", user.getDepartment().getId().toString());
        }
        return jwtUtil.generateToken(user.getEmail(), fullClaims, jwtExpirationMillis);
    }

    private ResponseCookie accessCookie(String accessToken) {
        return ResponseCookie.from("access_token", accessToken)
                .httpOnly(true).secure(authCookieSecure).sameSite("Strict")
                .path("/api").maxAge(jwtExpirationMillis / 1000).build();
    }

    private void setSessionCookies(HttpServletResponse response, String accessToken,
                                   RefreshSessionService.IssuedRefreshToken refresh) {
        ResponseCookie access = accessCookie(accessToken);
        long refreshMaxAge = Math.max(0, Duration.between(Instant.now(), refresh.expiresAt()).toSeconds());
        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", refresh.token())
                .httpOnly(true).secure(authCookieSecure).sameSite("Strict")
                .path("/api/v1/auth").maxAge(refreshMaxAge).build();
        response.addHeader(HttpHeaders.SET_COOKIE, access.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    /**
     * Admin-only: forcibly reset MFA for any user without requiring a TOTP code.
     * Useful for account recovery when a user has lost their authenticator device.
     *
     * DELETE /api/v1/mfa/admin/reset/{userId}
     * Requires ROLE_ADMIN authority.
     */
    @DeleteMapping("/admin/reset/{userId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_USERS','MANAGE_SECURITY_SETTINGS')")
    public ResponseEntity<Map<String, String>> adminResetMfa(@PathVariable UUID userId) {
        // Scoped to the caller's tenant. A bare findById here let an admin in one
        // organisation disable MFA for a user in another by guessing/leaking a UUID —
        // @PreAuthorize proves the caller is *an* admin, never that they administer
        // *this* user. Out-of-tenant ids now read as "not found".
        User target = userRepository.findByIdAndOrganisationId(userId, requireTenant())
                .orElse(null);

        if (target == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found."));
        }

        if (!Boolean.TRUE.equals(target.getMfaEnabled()) && target.getMfaSecret() == null) {
            return ResponseEntity.ok(Map.of("message", "MFA was not enabled for this user — no changes made."));
        }

        target.setMfaEnabled(false);
        target.setMfaSecret(null);
        sessionRevocationService.revokeAll(target);

        return ResponseEntity.ok(Map.of(
                "message", "MFA has been reset for user " + target.getEmail() + ". They can re-enrol at any time."
        ));
    }

    /**
     * Resolves the calling user within the current tenant.
     *
     * <p>Scoped by organisation because an email is only unique <em>per tenant</em> —
     * the same address may legitimately exist in two organisations, and an unscoped
     * {@code findByEmail} would resolve the wrong account (or throw on a non-unique
     * result). {@code TenantFilter} does not skip {@code /api/v1/mfa}, so every
     * authenticated request here is guaranteed to carry tenant context.
     */
    private User resolveUser(Authentication auth) {
        return userRepository.findByEmailAndOrganisationId(auth.getName(), requireTenant())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }

    /**
     * Fails closed when no tenant is resolved. {@code TenantFilter} already 403s
     * authenticated requests without an organisation, so reaching this is a bug
     * rather than a reachable state — it must never silently widen a query.
     */
    private UUID requireTenant() {
        UUID organisationId = TenantContext.getOrganisationId();
        if (organisationId == null) {
            throw new AccessDeniedException("No organisation context for this request.");
        }
        return organisationId;
    }
}
