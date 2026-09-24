package com.assetiq.security;

import com.assetiq.BaseIntegrationTest;
import com.assetiq.dto.TenantRegisterRequest;
import com.assetiq.models.RefreshSession;
import com.assetiq.repositories.RefreshSessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The replay grace window against the real persistence stack.
 *
 * <p>The behaviour that matters here cannot be shown with mocks: the guarantee that two
 * simultaneous presentations of one refresh token yield a single replacement rests on
 * the {@code PESSIMISTIC_WRITE} lock {@code findByTokenHash} takes, which only a real
 * database enforces.
 */
@DisplayName("Refresh replay grace window")
class RefreshGraceWindowIntegrationTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RefreshSessionRepository refreshSessionRepository;

    @Test
    @DisplayName("two devices presenting the same token get one replacement, and the family does not fork")
    void concurrentPresentationsYieldOneReplacement() throws Exception {
        Session session = signUp();
        UUID userId = session.userId();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch startLine = new CountDownLatch(1);
            List<Future<MvcResult>> attempts = List.of(
                    pool.submit(() -> refreshConcurrently(startLine, session.refreshToken())),
                    pool.submit(() -> refreshConcurrently(startLine, session.refreshToken())));
            startLine.countDown();

            String firstReplacement = null;
            for (Future<MvcResult> attempt : attempts) {
                MvcResult result = attempt.get(30, TimeUnit.SECONDS);
                assertThat(result.getResponse().getStatus()).isEqualTo(200);
                String token = objectMapper.readTree(result.getResponse().getContentAsString())
                        .get("refreshToken").asText();
                if (firstReplacement == null) firstReplacement = token;
                // The same replacement both times: nothing was minted twice.
                assertThat(token).isEqualTo(firstReplacement);
            }

            // Exactly one live token in the family. Two would be a fork.
            assertThat(liveSessionsOf(userId)).hasSize(1);
            // And it still works.
            mockMvc.perform(post("/api/v1/auth/refresh").header("X-Refresh-Token", firstReplacement))
                    .andExpect(status().isOk());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("a replay inside the window returns the same replacement over HTTP")
    void replayInsideTheWindowReturnsTheSameReplacement() throws Exception {
        Session session = signUp();

        String first = refresh(session.refreshToken()).get("refreshToken").asText();
        String replayed = refresh(session.refreshToken()).get("refreshToken").asText();

        assertThat(replayed).isEqualTo(first);
        assertThat(liveSessionsOf(session.userId())).hasSize(1);
    }

    @Test
    @DisplayName("outside the window the same presentation revokes the family")
    void replayOutsideTheWindowRevokesTheFamily() throws Exception {
        Session session = signUp();
        refresh(session.refreshToken());

        // Age the consumption past the grace window without waiting for it.
        List<RefreshSession> consumed = refreshSessionRepository.findAll().stream()
                .filter(s -> s.getUser().getId().equals(session.userId()) && s.getConsumedAt() != null)
                .toList();
        assertThat(consumed).isNotEmpty();
        consumed.forEach(s -> s.setConsumedAt(Instant.now().minusSeconds(3600)));
        refreshSessionRepository.saveAll(consumed);

        mockMvc.perform(post("/api/v1/auth/refresh").header("X-Refresh-Token", session.refreshToken()))
                .andExpect(status().isUnauthorized());

        assertThat(liveSessionsOf(session.userId())).isEmpty();
    }

    @Test
    @DisplayName("logout kills the family immediately, grace window or not")
    void logoutDefeatsTheGraceWindow() throws Exception {
        Session session = signUp();
        String head = refresh(session.refreshToken()).get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout").header("X-Refresh-Token", head))
                .andExpect(status().isOk());

        assertThat(liveSessionsOf(session.userId())).isEmpty();
        mockMvc.perform(post("/api/v1/auth/refresh").header("X-Refresh-Token", session.refreshToken()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").header("X-Refresh-Token", head))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a global revocation kills the family immediately, grace window or not")
    void globalRevocationDefeatsTheGraceWindow() throws Exception {
        Session session = signUp();
        JsonNode rotated = refresh(session.refreshToken());

        // SessionRevocationService.revokeAll — the same call a password change,
        // an MFA change, a deactivation and an erasure all make.
        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .header("Authorization", "Bearer " + rotated.get("token").asText())
                        .header("X-Organisation-Id", session.organisationId().toString()))
                .andExpect(status().isOk());

        assertThat(liveSessionsOf(session.userId())).isEmpty();
        mockMvc.perform(post("/api/v1/auth/refresh").header("X-Refresh-Token", session.refreshToken()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Refresh-Token", rotated.get("refreshToken").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a rejected refresh answers in the house envelope, with the legacy key kept")
    void rejectionUsesTheHouseErrorEnvelope() throws Exception {
        JsonNode body = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Refresh-Token", "not-a-real-refresh-token"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString());

        assertThat(body.hasNonNull("message")).isTrue();
        assertThat(body.get("message").asText()).isNotBlank();
        // Kept so any consumer still reading the old shape keeps working.
        assertThat(body.get("error").asText()).isEqualTo(body.get("message").asText());
    }

    @Test
    @DisplayName("an issued session carries both clocks, and rotation extends only the idle one")
    void rotationExtendsIdleButNotTheCap() throws Exception {
        Session session = signUp();
        RefreshSession issued = liveSessionsOf(session.userId()).get(0);
        Instant cap = issued.getFamilyExpiresAt();
        assertThat(cap).isNotNull().isAfter(issued.getExpiresAt().minusSeconds(1));

        refresh(session.refreshToken());

        RefreshSession head = liveSessionsOf(session.userId()).get(0);
        assertThat(head.getFamilyExpiresAt()).isEqualTo(cap);
        assertThat(head.getExpiresAt()).isBeforeOrEqualTo(cap);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private record Session(UUID userId, UUID organisationId, String refreshToken) {}

    private MvcResult refreshConcurrently(CountDownLatch startLine, String token) throws Exception {
        startLine.await(30, TimeUnit.SECONDS);
        return mockMvc.perform(post("/api/v1/auth/refresh").header("X-Refresh-Token", token)).andReturn();
    }

    private JsonNode refresh(String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Refresh-Token", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private List<RefreshSession> liveSessionsOf(UUID userId) {
        return refreshSessionRepository.findByUserIdAndRevokedAtIsNull(userId);
    }

    private Session signUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "grace+" + suffix + "@example.com";
        String password = "Password123";

        TenantRegisterRequest tenant = new TenantRegisterRequest();
        tenant.setOrganisationName("Grace Org " + suffix);
        tenant.setOrganisationContactEmail("ops+" + suffix + "@example.com");
        tenant.setAdminFirstName("Ama");
        tenant.setAdminLastName("Mensah");
        tenant.setAdminEmail(email);
        tenant.setPassword(password);
        tenant.setCountry("GH");
        tenant.setTimezone("UTC");
        tenant.setIndustry("IT");
        String organisationId = objectMapper.readTree(mockMvc.perform(post("/api/v1/tenant/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tenant)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("organisationId").asText();

        JsonNode login = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        return new Session(UUID.fromString(login.get("user").get("id").asText()),
                UUID.fromString(organisationId), login.get("refreshToken").asText());
    }
}
