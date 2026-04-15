package com.assetiq.controllers.v1;

import com.assetiq.dto.TenantRegisterRequest;
import com.assetiq.security.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void loginWithoutOrganisationId_returnsTokenForSingleOrganisationUser() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "login+" + suffix + "@example.com";
        String password = "Password123";

        TenantRegisterRequest request = new TenantRegisterRequest();
        request.setOrganisationName("Login Test Org " + suffix);
        request.setOrganisationContactEmail("ops+" + suffix + "@example.com");
        request.setAdminFirstName("Debbie");
        request.setAdminLastName("Fiator");
        request.setAdminEmail(email);
        request.setPassword(password);
        request.setCountry("GH");
        request.setTimezone("UTC");
        request.setIndustry("IT");

        mockMvc.perform(post("/api/v1/tenant/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        String responseBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode body = objectMapper.readTree(responseBody);
        assertThat(jwtUtil.parseToken(body.get("token").asText()).getSubject()).isEqualTo(email);
    }
}
