package com.assetiq.security;

import com.assetiq.dto.TenantRegisterRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Silent token refresh must work for a real user.
 *
 * <p>Regression: the refresh endpoint read the user's lazy role after the rotation
 * transaction had closed (open-in-view is off), so every refresh returned 500 and users
 * were logged out when their access token expired. Unit tests with mocked users never
 * saw it; this runs the real persistence stack.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Auth refresh")
class AuthRefreshIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtUtil jwtUtil;

    @Test
    @DisplayName("refresh issues a new access token carrying the role and organisation")
    void refreshKeepsRoleAndOrganisation() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "refresh+" + suffix + "@example.com";
        String password = "Password123";

        TenantRegisterRequest tenant = new TenantRegisterRequest();
        tenant.setOrganisationName("Refresh Org " + suffix);
        tenant.setOrganisationContactEmail("ops+" + suffix + "@example.com");
        tenant.setAdminFirstName("Kwame");
        tenant.setAdminLastName("Boateng");
        tenant.setAdminEmail(email);
        tenant.setPassword(password);
        tenant.setCountry("GH");
        tenant.setTimezone("UTC");
        tenant.setIndustry("IT");
        String orgId = objectMapper.readTree(mockMvc.perform(post("/api/v1/tenant/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tenant)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("organisationId").asText();

        JsonNode login = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        JsonNode refreshed = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Refresh-Token", login.get("refreshToken").asText()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        var claims = jwtUtil.parseToken(refreshed.get("token").asText());
        assertThat(claims.getSubject()).isEqualTo(email);
        assertThat(claims.get("role", String.class)).startsWith("ROLE_");
        assertThat(claims.get("organisationId", String.class)).isEqualTo(orgId);

        mockMvc.perform(get("/api/v1/assets").header("Authorization", "Bearer " + refreshed.get("token").asText()))
                .andExpect(status().isOk());
    }
}
