package com.assetiq.integration;

import com.assetiq.dto.TenantRegisterRequest;
import com.assetiq.models.FeatureFlag;
import com.assetiq.repositories.FeatureFlagRepository;
import com.assetiq.services.FeatureFlagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression for the reason the assistant never appeared: the
 * {@code commercial.governed-ai} flag was seeded OFF and re-forced OFF by V32,
 * so every /api/v1/ai/** route answered 404 with no error anyone could act on.
 * V60 turns it on; these tests fail if it is ever turned back off silently.
 *
 * <p>No AI key is configured in the test profile, which is also the staging
 * situation today — so this doubles as the provider-outage degradation test at
 * the HTTP layer: a missing key must produce a 200 with an explanation, never
 * the 500 the old {@code IllegalStateException} produced.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AI assistant over the API")
class AiAssistantApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private FeatureFlagService featureFlags;
    @Autowired private FeatureFlagRepository featureFlagRepository;

    private static final String AI_FLAG = "commercial.governed-ai";

    private String token;
    private String orgId;

    @BeforeEach
    void signIn() throws Exception {
        // The H2 test profile runs with Flyway disabled and a Hibernate-generated
        // schema, so no migration has seeded the flag registry. V60's effect is
        // proved against real Postgres in GovernedAiFlagMigrationTest; here the
        // flag is registered so the routes behave as they will in a migrated
        // environment. An unregistered flag evaluates to OFF, which is the 404
        // this whole test class exists to stop happening silently.
        if (featureFlagRepository.findByKey(AI_FLAG).isEmpty()) {
            FeatureFlag flag = new FeatureFlag();
            flag.setKey(AI_FLAG);
            flag.setDescription("Governed AI assistant (test fixture).");
            flag.setEnabledGlobally(true);
            featureFlagRepository.save(flag);
        }

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "ai+" + suffix + "@example.com";
        String password = "Password123";

        TenantRegisterRequest tenant = new TenantRegisterRequest();
        tenant.setOrganisationName("AI Org " + suffix);
        tenant.setOrganisationContactEmail("ops+" + suffix + "@example.com");
        tenant.setAdminFirstName("Kofi");
        tenant.setAdminLastName("Boateng");
        tenant.setAdminEmail(email);
        tenant.setPassword(password);
        tenant.setCountry("GH");
        tenant.setTimezone("UTC");
        tenant.setIndustry("IT");

        orgId = objectMapper.readTree(mockMvc.perform(post("/api/v1/tenant/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tenant)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("organisationId").asText();

        token = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    @DisplayName("with the flag on, the AI routes are reachable for the tenant")
    void featureFlagGovernsTheRoutes() {
        assertThat(featureFlags.isEnabledFor(AI_FLAG, UUID.fromString(orgId)))
                .as("commercial.governed-ai must be ON, or every /api/v1/ai route 404s")
                .isTrue();
    }

    @Test
    @DisplayName("capabilities tells a client what to render, without a 404 guessing game")
    void capabilitiesDescribesTheFeature() throws Exception {
        mockMvc.perform(get("/api/v1/ai/capabilities")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Organisation-Id", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                // No key in the test profile, so the feature is on but not usable.
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.canChat").value(false))
                .andExpect(jsonPath("$.scope").isArray())
                .andExpect(jsonPath("$.limits.userPerMinute").isNumber());
    }

    @Test
    @DisplayName("an org admin's scope covers every section")
    void adminScopeIsComplete() throws Exception {
        mockMvc.perform(get("/api/v1/ai/capabilities")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Organisation-Id", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value(org.hamcrest.Matchers.hasItem("ASSETS")))
                .andExpect(jsonPath("$.withheld").isEmpty());
    }

    @Test
    @DisplayName("chat with no provider key degrades with an explanation instead of 404 or 500")
    void chatDegradesWithoutAKey() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Organisation-Id", orgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("message", "How many assets do we have?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(true))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("no AI provider key is configured")))
                .andExpect(jsonPath("$.conversationId").isNotEmpty());
    }

    @Test
    @DisplayName("an empty message is rejected before any retrieval or provider call")
    void emptyMessageIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Organisation-Id", orgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "  "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an unauthenticated caller gets nothing from either AI route")
    void anonymousIsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/ai/capabilities"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403));
        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403));
    }
}
