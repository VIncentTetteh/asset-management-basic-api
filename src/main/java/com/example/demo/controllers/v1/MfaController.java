package com.example.demo.controllers.v1;

import com.example.demo.models.User;
import com.example.demo.repositories.UserRepository;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Map;

/**
 * TOTP-based Multi-Factor Authentication endpoints.
 *
 * Flow:
 *   1. POST /setup    → generates a TOTP secret + QR code (base64 PNG); user scans in authenticator app
 *   2. POST /verify   → confirms the first TOTP code and enables MFA on the account
 *   3. DELETE /disable → disables MFA after verifying a current TOTP code
 */
@RestController
@RequestMapping("/api/v1/mfa")
public class MfaController {

    private final UserRepository userRepository;
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(
            new dev.samstevens.totp.code.DefaultCodeGenerator(HashingAlgorithm.SHA1),
            new SystemTimeProvider());

    public MfaController(UserRepository userRepository) {
        this.userRepository = userRepository;
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

    private User resolveUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }
}
