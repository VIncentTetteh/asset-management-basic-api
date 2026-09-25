package com.assetiq.security;

import com.assetiq.dto.TenantRegisterRequest;
import com.assetiq.models.User;
import com.assetiq.repositories.UserRepository;
import com.assetiq.services.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Signup email-verification enforcement.
 *
 * <p>The rest of the suite runs with {@code app.auth.require-email-verification=false}
 * so existing tests can register a tenant and use its token immediately. This class
 * turns the gate back on, because a security control that is never exercised in the
 * configuration that ships is not a control.
 *
 * <p>{@link EmailService} is mocked so the verification link can be captured out of
 * the outgoing message — that also asserts the email is genuinely sent with a usable
 * link, rather than the token merely being written to the database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.auth.require-email-verification=true")
@DisplayName("Email verification enforcement")
class EmailVerificationEnforcementTest {

    private static final String PASSWORD = "Password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private EmailService emailService;

    @Test
    @DisplayName("a freshly registered user cannot sign in until they verify")
    void unverifiedUser_cannotLogIn() throws Exception {
        String email = register();

        mockMvc.perform(login(email))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.emailVerificationRequired").value(true));
    }

    @Test
    @DisplayName("verifying with the emailed token unblocks sign-in")
    void verifiedUser_canLogIn() throws Exception {
        String email = register();
        String token = captureVerificationToken(email);

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(login(email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    @DisplayName("the verification link is single-use")
    void verificationToken_cannotBeReplayed() throws Exception {
        String email = register();
        String token = captureVerificationToken(email);

        mockMvc.perform(verifyWith(token)).andExpect(status().isOk());
        // Replaying a link that leaked from an inbox or a proxy log must do nothing.
        mockMvc.perform(verifyWith(token)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an expired token is rejected and leaves the account unverified")
    void expiredToken_isRejected() throws Exception {
        String email = register();
        String token = captureVerificationToken(email);

        User user = userRepository.findAllByEmail(email).get(0);
        user.setEmailVerificationTokenExpiry(Instant.now().minusSeconds(60));
        userRepository.save(user);

        mockMvc.perform(verifyWith(token)).andExpect(status().isBadRequest());

        assertThat(userRepository.findAllByEmail(email).get(0).isEmailVerified())
                .as("an expired token must not verify the account")
                .isFalse();
    }

    @Test
    @DisplayName("a garbage token is rejected")
    void unknownToken_isRejected() throws Exception {
        mockMvc.perform(verifyWith("not-a-real-token")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("resend does not reveal whether an address is registered")
    void resend_doesNotEnumerateUsers() throws Exception {
        String registered = register();

        String knownResponse = mockMvc.perform(resendFor(registered))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String unknownResponse = mockMvc.perform(resendFor("nobody-" + UUID.randomUUID() + "@example.com"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownResponse)
                .as("a registered and an unregistered address must be indistinguishable")
                .isEqualTo(knownResponse);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Registers a tenant and returns the admin's email address. */
    private String register() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "verify+" + suffix + "@example.com";

        TenantRegisterRequest req = new TenantRegisterRequest();
        req.setOrganisationName("Verify Org " + suffix);
        req.setOrganisationContactEmail("ops+" + suffix + "@example.com");
        req.setAdminFirstName("Ama");
        req.setAdminLastName("Boateng");
        req.setAdminEmail(email);
        req.setPassword(PASSWORD);
        req.setCountry("GH");
        req.setTimezone("UTC");
        req.setIndustry("IT");

        mockMvc.perform(post("/api/v1/tenant/register")
                        .header("X-Forwarded-For", "10.1.0." + (Math.abs(suffix.hashCode()) % 250 + 1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        return email;
    }

    /** Pulls the raw token out of the verification email actually sent to this address. */
    @SuppressWarnings("unchecked")
    private String captureVerificationToken(String email) {
        ArgumentCaptor<Map<String, Object>> model = ArgumentCaptor.forClass(Map.class);

        verify(emailService, atLeastOnce())
                .sendTemplate(eq(email), anyString(), eq("email/verify-email"), model.capture());

        String verifyUrl = String.valueOf(model.getValue().get("verifyUrl"));
        assertThat(verifyUrl).contains("/verify-email?token=");

        return verifyUrl.substring(verifyUrl.indexOf("token=") + "token=".length());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String email)
            throws Exception {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD)));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder verifyWith(String token)
            throws Exception {
        return post("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", token)));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder resendFor(String email)
            throws Exception {
        return post("/api/v1/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email)));
    }
}
