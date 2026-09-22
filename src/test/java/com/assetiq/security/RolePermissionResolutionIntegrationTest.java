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
 * A user whose access comes from a role's permissions (every non-admin user) must
 * actually receive those permissions.
 *
 * <p>Regression: {@code PermissionCacheService} reached a role's lazily loaded
 * permission rows through a self-invocation, which bypasses the {@code @Transactional}
 * proxy. With open-session-in-view off there was no session, the lazy load threw, the
 * service swallowed it and returned no permissions — so every non-admin user got 403
 * on everything, while admins (who pass on their ROLE_ authority) looked fine.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Role permission resolution")
class RolePermissionResolutionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PermissionCacheService permissionCacheService;

    @Test
    @DisplayName("a user with a standard role gets that role's permissions")
    void roleUserReceivesRolePermissions() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String adminEmail = "admin+" + suffix + "@example.com";
        String password = "Password123";

        TenantRegisterRequest tenant = new TenantRegisterRequest();
        tenant.setOrganisationName("Role Resolution Org " + suffix);
        tenant.setOrganisationContactEmail("ops+" + suffix + "@example.com");
        tenant.setAdminFirstName("Ama");
        tenant.setAdminLastName("Mensah");
        tenant.setAdminEmail(adminEmail);
        tenant.setPassword(password);
        tenant.setCountry("GH");
        tenant.setTimezone("UTC");
        tenant.setIndustry("IT");
        JsonNode registered = objectMapper.readTree(mockMvc.perform(post("/api/v1/tenant/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tenant)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        String orgId = registered.get("organisationId").asText();

        String adminToken = login(adminEmail, password);
        JsonNode roles = objectMapper.readTree(mockMvc.perform(get("/api/v1/roles")
                        .param("organisationId", orgId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String financeRoleId = null;
        for (JsonNode role : roles.isArray() ? roles : roles.path("items")) {
            if ("FINANCE_MANAGER".equals(role.path("name").asText())) {
                financeRoleId = role.path("id").asText();
            }
        }
        assertThat(financeRoleId).as("standard roles are seeded for a self-registered tenant").isNotNull();

        String userEmail = "finance+" + suffix + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Efua", "lastName", "Owusu", "email", userEmail,
                                "password", password, "roleId", financeRoleId, "organisationId", orgId))))
                .andExpect(status().isCreated());

        assertThat(permissionCacheService.getPermissionsForUser(userEmail, orgId))
                .contains("VIEW_ASSETS", "APPROVE_PROCUREMENT", "MANAGE_EXPENSES");

        String userToken = login(userEmail, password);
        mockMvc.perform(get("/api/v1/assets").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }
}
