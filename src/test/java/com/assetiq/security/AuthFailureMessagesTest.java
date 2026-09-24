package com.assetiq.security;

import com.assetiq.dto.TenantRegisterRequest;
import com.assetiq.enums.UserStatus;
import com.assetiq.models.User;
import com.assetiq.repositories.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every way a sign-in can fail must tell the user what to do.
 *
 * <p>A user on staging saw the single word "Forbidden" because the server put its
 * explanation under {@code error} while every client reads {@code message}. These tests
 * pin the shape a client can rely on: {@code message} always present, the legacy
 * {@code error} alias preserved, a stable {@code errorCode}, and machine-readable flags
 * so a client can decide what to offer without matching English.
 *
 * <p>They also pin the property that matters more than any of that: a wrong password
 * and an unregistered address stay byte-identical.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Authentication failures are actionable")
class AuthFailureMessagesTest {

    private static final String PASSWORD = "Password123!";
    private static final String LOGIN_URL = "/api/v1/auth/login";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;

    // ── Invalid credentials — unchanged, and still indistinguishable ──────────

    @Test
    @DisplayName("wrong password → 401 carrying message, error and errorCode")
    void wrongPassword_carriesMessage() throws Exception {
        String email = register();

        mockMvc.perform(login(email, "WrongPassword123!"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.error").value("Invalid email or password"))
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("a wrong password and an unknown address answer identically")
    void wrongPassword_andUnknownAddress_areIndistinguishable() throws Exception {
        String email = register();

        String wrongPassword = mockMvc.perform(login(email, "WrongPassword123!"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknownAddress = mockMvc.perform(
                        login("nobody-" + UUID.randomUUID() + "@example.com", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownAddress)
                .as("a wrong password must not be distinguishable from an unregistered address")
                .isEqualTo(wrongPassword);
    }

    // ── Account state ────────────────────────────────────────────────────────

    @Test
    @DisplayName("an inactive account → 403 naming who can reactivate it")
    void inactiveAccount_saysWhatToDo() throws Exception {
        String email = register();
        setStatus(email, UserStatus.INACTIVE);

        mockMvc.perform(login(email, PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("administrator")))
                .andExpect(jsonPath("$.error", containsString("administrator")))
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_ACTIVE"))
                .andExpect(jsonPath("$.accountStatus").value("INACTIVE"));
    }

    @Test
    @DisplayName("a suspended account → 403 with its own status flag")
    void suspendedAccount_saysWhatToDo() throws Exception {
        String email = register();
        setStatus(email, UserStatus.SUSPENDED);

        mockMvc.perform(login(email, PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("suspended")))
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_ACTIVE"))
                .andExpect(jsonPath("$.accountStatus").value("SUSPENDED"));
    }

    @Test
    @DisplayName("the account-state answer still requires the right password")
    void inactiveAccount_withWrongPassword_looksLikeAnyBadLogin() throws Exception {
        String email = register();
        setStatus(email, UserStatus.SUSPENDED);

        // The state of an account must not leak to someone who cannot prove the password.
        mockMvc.perform(login(email, "WrongPassword123!"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.accountStatus").doesNotExist());
    }

    // ── Lockout ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a locked account → 423 with a countdown and a way out")
    void lockedAccount_saysWhatToDo() throws Exception {
        String email = register();
        User user = userRepository.findAllByEmail(email).get(0);
        user.setFailedLoginAttempts(10);
        user.setLockedUntil(Instant.now().plusSeconds(900));
        userRepository.save(user);

        mockMvc.perform(login(email, PASSWORD))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.message", containsString("Account temporarily locked")))
                .andExpect(jsonPath("$.message", containsString("reset your password")))
                .andExpect(jsonPath("$.error", containsString("Account temporarily locked")))
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.accountLocked").value(true))
                .andExpect(jsonPath("$.lockedUntil").isString())
                .andExpect(jsonPath("$.retryAfterSeconds", greaterThan(0)));
    }

    @Test
    @DisplayName("resetting the password clears the lockout the message points at")
    void passwordReset_clearsLockout() throws Exception {
        String email = register();
        User user = userRepository.findAllByEmail(email).get(0);
        user.setFailedLoginAttempts(10);
        user.setLockedUntil(Instant.now().plusSeconds(900));
        userRepository.save(user);

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk());

        // The raw token only exists in the email; drive the reset the way the user would
        // by reading it back through the same hash the controller stores.
        String rawToken = resetTokenFor(email);
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", rawToken, "newPassword", "BrandNewPass123!"))))
                .andExpect(status().isOk());

        User after = userRepository.findAllByEmail(email).get(0);
        assertThat(after.isLockedOut()).as("a reset must lift the lockout").isFalse();
        assertThat(after.getFailedLoginAttempts()).isZero();
    }

    @Test
    @DisplayName("an expired reset link explains how to get another")
    void expiredResetToken_saysWhatToDo() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", "not-a-real-token", "newPassword", "BrandNewPass123!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Request a new one")))
                .andExpect(jsonPath("$.errorCode").value("RESET_TOKEN_INVALID"));
    }

    // ── Resend verification is reachable with no session ─────────────────────

    @Test
    @DisplayName("resend-verification answers an unauthenticated caller")
    void resendVerification_isPublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "someone-" + UUID.randomUUID() + "@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isString());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * The reset token is delivered by email only. This test needs the raw value, and
     * only the SHA-256 hash is stored, so the token is re-issued here through the same
     * public endpoint and located by scanning for the account whose stored hash matches.
     */
    private String resetTokenFor(String email) {
        // Deterministic alternative to intercepting the mail: set a known token directly,
        // hashed exactly as the controller does when it looks one up.
        String raw = "reset-" + UUID.randomUUID();
        User user = userRepository.findAllByEmail(email).get(0);
        user.setResetPasswordToken(sha256Hex(raw));
        user.setResetPasswordTokenExpiry(Instant.now().plusSeconds(3600));
        user.setResetPasswordTokenUsed(false);
        userRepository.save(user);
        return raw;
    }

    private static String sha256Hex(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void setStatus(String email, UserStatus status) {
        User user = userRepository.findAllByEmail(email).get(0);
        user.setStatus(status);
        userRepository.save(user);
    }

    private String register() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "authmsg+" + suffix + "@example.com";

        TenantRegisterRequest req = new TenantRegisterRequest();
        req.setOrganisationName("Auth Message Org " + suffix);
        req.setOrganisationContactEmail("ops+" + suffix + "@example.com");
        req.setAdminFirstName("Kofi");
        req.setAdminLastName("Owusu");
        req.setAdminEmail(email);
        req.setPassword(PASSWORD);
        req.setCountry("GH");
        req.setTimezone("UTC");
        req.setIndustry("IT");

        mockMvc.perform(post("/api/v1/tenant/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        return email;
    }

    private MockHttpServletRequestBuilder login(String email, String password) throws Exception {
        return post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password)));
    }
}
