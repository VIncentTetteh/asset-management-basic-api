package com.assetiq.services;

import com.assetiq.models.User;
import com.assetiq.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Issues and redeems signup email-verification tokens.
 *
 * <p>Shared by tenant registration (which issues the first token) and
 * {@code AuthController} (resend + redeem), so the token format and the
 * store-only-the-hash rule live in exactly one place.
 *
 * <p>The scheme deliberately mirrors the password-reset tokens already in
 * {@code AuthController}: 32 random bytes concatenated with an HMAC-SHA256
 * signature, emailed to the user in full, with only the SHA-256 hash persisted.
 * A database breach therefore yields no usable tokens.
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    /** How long a verification link stays valid. Long enough to survive a spam folder. */
    public static final Duration TOKEN_TTL = Duration.ofHours(48);

    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.email.base-url:http://localhost:3000}")
    private String emailBaseUrl;

    public EmailVerificationService(UserRepository userRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    /**
     * Issue a fresh token for the user and email them the verification link.
     *
     * <p>Never throws: a mail failure must not roll back or fail the signup that
     * triggered it. The user can always ask for another link.
     */
    public void sendVerificationEmail(User user) {
        if (user.isEmailVerified()) {
            return;
        }
        try {
            String rawToken = generateToken(user);

            // Issuing a new token invalidates the previous one — the stored hash is
            // overwritten, so an older link in an older email stops working.
            user.setEmailVerificationToken(sha256Hex(rawToken));
            user.setEmailVerificationTokenExpiry(Instant.now().plus(TOKEN_TTL));
            userRepository.save(user);

            String verifyUrl = emailBaseUrl.replaceAll("/+$", "") + "/verify-email?token=" + rawToken;

            Map<String, Object> model = new HashMap<>();
            model.put("firstName", user.getFirstName());
            model.put("email", user.getEmail());
            model.put("verifyUrl", verifyUrl);
            model.put("expiresHours", TOKEN_TTL.toHours());

            emailService.sendTemplate(user.getEmail(), "Verify your email address",
                    "email/verify-email", model);
        } catch (Exception e) {
            // Log without the address — see the PII rule in the logging standards.
            log.error("[AUTH] Failed to issue verification email for user {}", user.getId(), e);
        }
    }

    /**
     * Redeem a raw token from a verification link.
     *
     * @return the now-verified user, or empty if the token is unknown or expired.
     */
    public Optional<User> verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        Optional<User> match = userRepository.findByEmailVerificationToken(sha256Hex(rawToken));
        if (match.isEmpty()) {
            return Optional.empty();
        }

        User user = match.get();
        if (user.getEmailVerificationTokenExpiry() == null
                || user.getEmailVerificationTokenExpiry().isBefore(Instant.now())) {
            log.warn("[AUTH] Expired verification token presented for user {}", user.getId());
            return Optional.empty();
        }

        user.setEmailVerifiedAt(Instant.now());
        // Clear the token so the link is single-use.
        user.setEmailVerificationToken(null);
        user.setEmailVerificationTokenExpiry(null);
        userRepository.save(user);

        log.info("[AUTH] Email verified for user {}", user.getId());
        return Optional.of(user);
    }

    private String generateToken(User user) throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] randomBytes = new byte[32];
        random.nextBytes(randomBytes);

        String data = Base64.getEncoder().encodeToString(randomBytes)
                + user.getId()
                + System.currentTimeMillis();

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

        byte[] combined = new byte[randomBytes.length + signature.length];
        System.arraycopy(randomBytes, 0, combined, 0, randomBytes.length);
        System.arraycopy(signature, 0, combined, randomBytes.length, signature.length);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
