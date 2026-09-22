package com.assetiq.integration;

import com.assetiq.dto.TenantRegisterRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import com.assetiq.dto.RoleDto;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.services.RoleService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Two failures found by the staging round-trip suite, both invisible to mocked unit
 * tests: saving a role that keeps any of its permissions, and listing vendor reviews.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Roles and vendor reviews over the API")
class RoleAndVendorReviewApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RoleService roleService;

    private String token;
    private String orgId;

    @BeforeEach
    void signIn() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "roles+" + suffix + "@example.com";
        String password = "Password123";

        TenantRegisterRequest tenant = new TenantRegisterRequest();
        tenant.setOrganisationName("Roles Org " + suffix);
        tenant.setOrganisationContactEmail("ops+" + suffix + "@example.com");
        tenant.setAdminFirstName("Abena");
        tenant.setAdminLastName("Darko");
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
    @DisplayName("editing a role that keeps a permission saves (no duplicate-row collision)")
    void roleEditKeepingAPermissionSaves() {
        // Through the service: the HTTP path additionally demands a fresh MFA proof,
        // which is not what this regression is about.
        TenantContext.setOrganisationId(UUID.fromString(orgId));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin@example.com", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            RoleDto created = new RoleDto();
            created.setName("E2E_ROLE_" + UUID.randomUUID().toString().substring(0, 6));
            created.setDescription("before");
            created.setPermissions(List.of("VIEW_ASSETS", "VIEW_REPORTS"));
            RoleDto saved = roleService.createRole(created, UUID.fromString(orgId));

            // VIEW_ASSETS stays, VIEW_REPORTS goes, VIEW_SUPPLIERS arrives.
            RoleDto patch = new RoleDto();
            patch.setDescription("after");
            patch.setPermissions(List.of("VIEW_ASSETS", "VIEW_SUPPLIERS"));

            assertThat(roleService.patchRole(saved.getId(), patch).getPermissions())
                    .containsExactlyInAnyOrder("VIEW_ASSETS", "VIEW_SUPPLIERS");
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("vendor reviews list loads with the supplier name")
    void vendorReviewListLoads() throws Exception {
        String supplierId = objectMapper.readTree(mockMvc.perform(post("/api/v1/suppliers")
                        .param("organisationId", orgId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Melcom Enterprise"))))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/vendor-reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "supplierId", supplierId, "rating", 4.0,
                                "qualityScore", 4, "deliveryScore", 4, "supportScore", 5,
                                "periodStart", "2026-01-01", "periodEnd", "2026-03-31"))))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get("/api/v1/vendor-reviews").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].supplierName").value("Melcom Enterprise"));
    }
}
