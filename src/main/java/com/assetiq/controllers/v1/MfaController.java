package com.assetiq.controllers.v1;

import com.assetiq.models.User;
import com.assetiq.repositories.UserRepository;
import com.assetiq.security.JwtUtil;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
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
 */
@RestController
@RequestMapping("/api/v1/mfa")
public class MfaController {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(
            new dev.samstevens.totp.code.DefaultCodeGenerator(HashingAlgorithm.SHA1),
            new SystemTimeProvider());

    @Value("${app.jwt.expiration:86400000}")
    private long jwtExpirationMillis;

    public MfaController(UserRepository userRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
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

        String secret = secretGenerator.generate();
        user.setMfaSecret(secret);
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

        if (!codeVerifier.isValidCode(user.getMfaSecret(), code)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid TOTP code."));
        }

        user.setMfaEnabled(true);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "MFA enabled successfully."));
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

        if (!codeVerifier.isValidCode(user.getMfaSecret(), code)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid TOTP code."));
        }

        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "MFA disabled successfully."));
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
    public ResponseEntity<?> challenge(@RequestBody Map<String, String> body) {
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

        // Verify the TOTP code
        if (user.getMfaSecret() == null || !codeVerifier.isValidCode(user.getMfaSecret(), code)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid authenticator code."));
        }

        // Update last login timestamp
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Build and return the full JWT
        Map<String, Object> fullClaims = new HashMap<>();
        fullClaims.put("email", user.getEmail());
        fullClaims.put("firstName", user.getFirstName());
        fullClaims.put("lastName", user.getLastName());

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

        String token = jwtUtil.generateToken(user.getEmail(), fullClaims, jwtExpirationMillis);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "user", Map.of(
                        "id", user.getId(),
                        "email", user.getEmail(),
                        "firstName", user.getFirstName(),
                        "lastName", user.getLastName(),
                        "role", user.getRole() != null ? user.getRole().getName() : "NONE"),
                "expiresIn", jwtExpirationMillis / 1000));
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
        User target = userRepository.findById(userId)
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
        userRepository.save(target);

        return ResponseEntity.ok(Map.of(
                "message", "MFA has been reset for user " + target.getEmail() + ". They can re-enrol at any time."
        ));
    }

    private User resolveUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }
}
