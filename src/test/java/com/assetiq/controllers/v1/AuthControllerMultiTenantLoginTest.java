package com.assetiq.controllers.v1;

import com.assetiq.BaseIntegrationTest;
import com.assetiq.dto.TenantRegisterRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login for an email that is registered in several organisations: the organisation
 * list is only revealed to a caller who has proved the password for each listed tenant.
 */
@DisplayName("AuthController — email in several organisations")
class AuthControllerMultiTenantLoginTest extends BaseIntegrationTest {

    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String SHARED_PASSWORD = "Password123!";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String email;
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        email = "multi+" + suffix + "@example.com";
    }

    @Test
    @DisplayName("same password in two tenants -> 409 listing both organisations; organisationId then signs in")
    void samePassword_listsOrganisations() throws Exception {
        register("Alpha " + suffix, SHARED_PASSWORD);
        register("Beta " + suffix, SHARED_PASSWORD);

        String body = mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON)
                        .content(login(SHARED_PASSWORD, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANISATION_REQUIRED"))
                .andExpect(jsonPath("$.organisations.length()").value(2))
                .andExpect(jsonPath("$.organisations[0].name").value("Alpha " + suffix))
                .andReturn().getResponse().getContentAsString();

        String orgId = objectMapper.readTree(body).path("organisations").get(1).path("id").asText();
        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON)
                        .content(login(SHARED_PASSWORD, orgId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    @DisplayName("wrong password -> the same 401 as an unknown email, no organisation list")
    void wrongPassword_revealsNothing() throws Exception {
        register("Gamma " + suffix, SHARED_PASSWORD);
        register("Delta " + suffix, SHARED_PASSWORD);

        String body = mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON)
                        .content(login("Wrong-password-1", null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        assertThat(json.has("organisations")).isFalse();
    }

    @Test
    @DisplayName("password accepted by only one tenant -> signs straight in to that tenant")
    void distinctPasswords_signInToTheMatchingTenant() throws Exception {
        register("Epsilon " + suffix, SHARED_PASSWORD);
        register("Zeta " + suffix, "Another-pass-99");

        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON)
                        .content(login("Another-pass-99", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
    }

    private void register(String orgName, String password) throws Exception {
        TenantRegisterRequest req = new TenantRegisterRequest();
        req.setOrganisationName(orgName);
        req.setAdminFirstName("Ama");
        req.setAdminLastName("Mensah");
        req.setAdminEmail(email);
        req.setPassword(password);
        req.setCountry("GH");
        req.setTimezone("UTC");
        req.setIndustry("Banking");
        mockMvc.perform(post("/api/v1/tenant/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    private String login(String password, String organisationId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        if (organisationId != null) {
            body.put("organisationId", organisationId);
        }
        return objectMapper.writeValueAsString(body);
    }
}
