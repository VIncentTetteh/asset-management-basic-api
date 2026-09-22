package com.assetiq.controllers.v1;

import com.assetiq.BaseIntegrationTest;
import com.assetiq.dto.TenantRegisterRequest;
import com.assetiq.dto.TenantRegisterResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Password re-confirmation and self-service account deletion — the two endpoints
 * the mobile app was working around, one by calling {@code /login} again and the
 * other by filing a DSAR nobody could act on.
 */
@DisplayName("Password re-confirmation and self-service deletion")
class SelfServiceAccountDeletionIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "Password123";
    private static final String MEMBER_PASSWORD = "MemberPass456";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String organisationId;
    private String adminToken;
    private String memberEmail;

    @BeforeEach
    void signIn() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        TenantRegisterRequest req = new TenantRegisterRequest();
        req.setOrganisationName("Deletion Org " + suffix);
        req.setAdminFirstName("Yaa");
        req.setAdminLastName("Owner");
        req.setAdminEmail("owner+" + suffix + "@example.com");
        req.setPassword(PASSWORD);
        req.setCountry("GH");
        MvcResult reg = mockMvc.perform(post("/api/v1/tenant/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn();
        organisationId = objectMapper.readValue(reg.getResponse().getContentAsString(),
                TenantRegisterResponse.class).getOrganisationId().toString();
        adminToken = signIn(req.getAdminEmail(), PASSWORD);

        memberEmail = "member+" + suffix + "@example.com";
        Map<String, Object> member = new HashMap<>();
        member.put("firstName", "Kwame");
        member.put("lastName", "Member");
        member.put("email", memberEmail);
        member.put("password", MEMBER_PASSWORD);
        send(post("/api/v1/users"), member, adminToken).andExpect(status().is2xxSuccessful());
    }

    // ── POST /api/v1/auth/verify-password ────────────────────────────────────

    @Test
    void theRightPasswordIs204AndTheWrongOneIs401() throws Exception {
        String token = signIn(memberEmail, MEMBER_PASSWORD);
        send(post("/api/v1/auth/verify-password"), Map.of("password", MEMBER_PASSWORD), token)
                .andExpect(status().isNoContent());
        send(post("/api/v1/auth/verify-password"), Map.of("password", "not-the-password"), token)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyPasswordIssuesNothing() throws Exception {
        String token = signIn(memberEmail, MEMBER_PASSWORD);
        MvcResult result = send(post("/api/v1/auth/verify-password"), Map.of("password", MEMBER_PASSWORD), token)
                .andExpect(status().isNoContent()).andReturn();
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
    }

    @Test
    void verifyPasswordNeedsASession() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-password").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", MEMBER_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyPasswordRequiresAPassword() throws Exception {
        String token = signIn(memberEmail, MEMBER_PASSWORD);
        send(post("/api/v1/auth/verify-password"), Map.of("password", "  "), token)
                .andExpect(status().isBadRequest());
    }

    @Test
    void aWrongPasswordDoesNotCountTowardsTheLockout() throws Exception {
        String token = signIn(memberEmail, MEMBER_PASSWORD);
        for (int attempt = 0; attempt < 3; attempt++) {
            send(post("/api/v1/auth/verify-password"), Map.of("password", "wrong-" + attempt), token)
                    .andExpect(status().isUnauthorized());
        }
        // Still able to sign in: re-confirming is not a sign-in attempt.
        assertThat(signIn(memberEmail, MEMBER_PASSWORD)).isNotBlank();
    }

    // ── DELETE /api/v1/users/me ──────────────────────────────────────────────

    @Test
    void deletingYourOwnAccountNeedsFreshMfa() throws Exception {
        String token = signIn(memberEmail, MEMBER_PASSWORD);
        // No MFA enrolled at all: the step-up gate refuses before the password is read.
        send(delete("/api/v1/users/me"), Map.of("password", MEMBER_PASSWORD), token)
                .andExpect(status().is4xxClientError());
    }

    @Test
    void aMemberCanDeleteTheirOwnAccountAndIsThenGone() throws Exception {
        String token = steppedUpSession(memberEmail, MEMBER_PASSWORD);

        // The wrong password is refused even with a fresh step-up.
        send(delete("/api/v1/users/me"), Map.of("password", "wrong"), token)
                .andExpect(status().isBadRequest());

        send(delete("/api/v1/users/me"), Map.of("password", MEMBER_PASSWORD), token)
                .andExpect(status().isNoContent());

        // The session is revoked immediately, not at token expiry.
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().is4xxClientError());
        // And the credentials no longer work.
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", memberEmail,
                                "password", MEMBER_PASSWORD, "organisationId", organisationId))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void theErasedAccountKeepsNoIdentifyingDetail() throws Exception {
        String token = steppedUpSession(memberEmail, MEMBER_PASSWORD);
        send(delete("/api/v1/users/me"), Map.of("password", MEMBER_PASSWORD), token)
                .andExpect(status().isNoContent());

        // The admin's user list no longer carries the person's name or address.
        String users = mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(users).doesNotContain(memberEmail).doesNotContain("Kwame");
    }

    @Test
    void theLastAdministratorIsRefused() throws Exception {
        String token = steppedUpSession(ownerEmail(), PASSWORD);
        send(delete("/api/v1/users/me"), Map.of("password", PASSWORD), token)
                .andExpect(status().isConflict());
        // Still signed in and still there.
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String ownerEmail() throws Exception {
        return json(mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn()).path("email").asText();
    }

    private String signIn(String email, String password) throws Exception {
        JsonNode login = json(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password,
                                "organisationId", organisationId))))
                .andExpect(status().isOk()).andReturn());
        return login.path("token").asText();
    }

    /** Signs in, enrols an authenticator, signs in through the challenge, then steps up. */
    private String steppedUpSession(String email, String password) throws Exception {
        String token = signIn(email, password);
        String secret = json(mockMvc.perform(post("/api/v1/mfa/setup")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn()).path("secret").asText();
        send(post("/api/v1/mfa/verify"), Map.of("code", totp(secret)), token).andExpect(status().isOk());

        // Enrolling revokes every session, so sign in again — now through the challenge.
        JsonNode challenge = json(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password,
                                "organisationId", organisationId))))
                .andExpect(status().isAccepted()).andReturn());
        JsonNode session = json(mockMvc.perform(post("/api/v1/mfa/challenge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "mfaChallengeToken", challenge.path("mfaChallengeToken").asText(),
                                "code", totp(secret)))))
                .andExpect(status().isOk()).andReturn());
        String signedIn = session.path("token").asText();

        JsonNode steppedUp = json(mockMvc.perform(post("/api/v1/mfa/step-up")
                        .header("Authorization", "Bearer " + signedIn)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", totp(secret)))))
                .andExpect(status().isOk()).andReturn());
        return steppedUp.path("token").asText();
    }

    private static String totp(String secret) throws Exception {
        return new DefaultCodeGenerator(HashingAlgorithm.SHA1)
                .generate(secret, Instant.now().getEpochSecond() / 30);
    }

    private ResultActions send(MockHttpServletRequestBuilder builder, Map<String, Object> body, String token)
            throws Exception {
        return mockMvc.perform(builder.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
