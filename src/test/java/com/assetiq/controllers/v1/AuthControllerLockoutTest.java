package com.assetiq.controllers.v1;

import com.assetiq.BaseIntegrationTest;
import com.assetiq.dto.TenantRegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the brute-force lockout logic in AuthController.
 *
 * <p>Covers:
 * <ul>
 *   <li>Failed attempts are counted correctly</li>
 *   <li>Account locks after MAX_FAILED_LOGIN_ATTEMPTS (10) failures → HTTP 423</li>
 *   <li>A locked account rejects even the correct password with HTTP 423</li>
 *   <li>Successful login resets the failed-attempts counter to zero</li>
 *   <li>Counter resets on success, allowing re-lockout after a new failure run</li>
 * </ul>
 *
 * <p>Runs against a real Postgres + Redis pair provided by {@link BaseIntegrationTest}.
 */
@DisplayName("AuthController — brute-force lockout")
class AuthControllerLockoutTest extends BaseIntegrationTest {

    private static final String LOGIN_URL    = "/api/v1/auth/login";
    private static final String REGISTER_URL = "/api/v1/tenant/register";

    private static final int MAX_ATTEMPTS = 10;   // must match AuthController constant

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String userEmail;
    private String correctPassword;

    @BeforeEach
    void registerFreshUser() throws Exception {
        // Each test gets its own isolated user so failures don't bleed across tests
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        userEmail       = "lockout+" + suffix + "@example.com";
        correctPassword = "Password123!";

        TenantRegisterRequest req = new TenantRegisterRequest();
        req.setOrganisationName("Lockout Test Org " + suffix);
        req.setOrganisationContactEmail("ops+" + suffix + "@example.com");
        req.setAdminFirstName("Ama");
        req.setAdminLastName("Mensah");
        req.setAdminEmail(userEmail);
        req.setPassword(correctPassword);
        req.setCountry("GH");
        req.setTimezone("UTC");
        req.setIndustry("Banking");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    // ── Happy-path (sanity) ───────────────────────────────────────────────────

    @Test
    @DisplayName("correct credentials → 200 OK with token")
    void correctCredentials_returns200() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(userEmail, correctPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
    }

    // ── Counter increments ────────────────────────────────────────────────────

    @Test
    @DisplayName("wrong password → 401 Unauthorized (below lockout threshold)")
    void wrongPassword_below_threshold_returns401() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(userEmail, "WrongPassword1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));
    }

    @Test
    @DisplayName("9 failed attempts do not lock the account — 10th attempt still 401")
    void nineFailedAttempts_doesNotLock() throws Exception {
        for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(userEmail, "Wrong" + i)))
                    .andExpect(status().isUnauthorized());
        }

        // 9th failure — account is not yet locked
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(userEmail, "StillWrong")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));
    }

    // ── Lockout triggers ──────────────────────────────────────────────────────

    @Test
    @DisplayName("10th failed attempt locks the account → next attempt returns 423")
    void tenthFailedAttempt_locksAccount() throws Exception {
        triggerLockout();

        // Next attempt (even with wrong password) must be 423 Locked
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(userEmail, "AnyPassword")))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error", containsString("Account temporarily locked")));
    }

    @Test
    @DisplayName("correct password after lockout → 423 Locked (not 200)")
    void correctPasswordAfterLockout_returns423() throws Exception {
        triggerLockout();

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(userEmail, correctPassword)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error", containsString("Account temporarily locked")));
    }

    // ── Counter reset on success ──────────────────────────────────────────────

    @Test
    @DisplayName("successful login resets counter — subsequent login still works")
    void successfulLogin_resetsCounter_subsequentLoginSucceeds() throws Exception {
        // Run 5 failures (below threshold)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(userEmail, "WrongPass" + i)))
                    .andExpect(status().isUnauthorized());
        }

        // Successful login should reset the counter
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(userEmail, correctPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());

        // Another successful login must still work (counter was cleared)
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(userEmail, correctPassword)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("after a successful login, a fresh run of 10 failures re-locks the account")
    void afterSuccessfulLogin_freshLockoutCycleWorks() throws Exception {
        // First: 5 failures + 1 success (counter reset)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(userEmail, "Fail" + i)))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(userEmail, correctPassword)))
                .andExpect(status().isOk());

        // Now run a full lockout cycle from zero
        triggerLockout();

        // Account must be locked again
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(userEmail, correctPassword)))
                .andExpect(status().isLocked());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Submits exactly {@code MAX_ATTEMPTS} wrong-password requests, which should
     * trip the lockout on the last attempt (HTTP 401 — the lock is set but the
     * response is still UNAUTHORIZED for that request).
     */
    private void triggerLockout() throws Exception {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(userEmail, "BadPassword" + i)))
                    .andExpect(status().isUnauthorized());
        }
    }

    private String loginBody(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(
                Map.of("email", email, "password", password));
    }
}
