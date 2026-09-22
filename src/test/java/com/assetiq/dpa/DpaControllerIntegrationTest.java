package com.assetiq.dpa;

import com.assetiq.dto.TenantRegisterRequest;
import com.assetiq.dto.TenantRegisterResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The DPA endpoints took @RequestAttribute("currentUser"/"currentOrg") that no
 * filter set, so every call failed. This drives them end to end.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("DPA consent and DSAR endpoints work for a signed-in tenant")
class DpaControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void consentLifecycle() throws Exception {
        String token = registerAndLogin();
        mockMvc.perform(auth(post("/api/v1/dpa/consent"), token).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("purpose", "MARKETING", "granted", true))))
                .andExpect(status().isOk());
        assertThat(body(mockMvc.perform(auth(get("/api/v1/dpa/consent/check?purpose=MARKETING"), token))
                .andExpect(status().isOk()).andReturn()).asBoolean()).isTrue();
        JsonNode page = body(mockMvc.perform(auth(get("/api/v1/dpa/consent"), token))
                .andExpect(status().isOk()).andReturn());
        assertThat(page.path("content")).hasSize(1);
        mockMvc.perform(auth(delete("/api/v1/dpa/consent/MARKETING"), token)).andExpect(status().isOk());
        assertThat(body(mockMvc.perform(auth(get("/api/v1/dpa/consent/check?purpose=MARKETING"), token))
                .andReturn()).asBoolean()).isFalse();
    }

    @Test
    void dsarLifecycleAndClosedRequestsStayClosed() throws Exception {
        String token = registerAndLogin();
        JsonNode created = body(mockMvc.perform(auth(post("/api/v1/dpa/dsar"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "requesterEmail", "subject@example.com", "requestType", "ACCESS"))))
                .andExpect(status().isCreated()).andReturn());
        String id = created.path("id").asText();
        assertThat(created.path("dueAt").asText()).isNotBlank();

        // The summary travels in a JSON body (it may hold personal data), and "" clears it.
        JsonNode drafted = body(mockMvc.perform(auth(patch("/api/v1/dpa/dsar/" + id + "/status"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "IN_PROGRESS", "responseSummary", "Draft"))))
                .andExpect(status().isOk()).andReturn());
        assertThat(drafted.path("responseSummary").asText()).isEqualTo("Draft");
        JsonNode cleared = body(mockMvc.perform(auth(patch("/api/v1/dpa/dsar/" + id + "/status"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "IN_PROGRESS", "responseSummary", ""))))
                .andExpect(status().isOk()).andReturn());
        JsonNode clearedSummary = cleared.path("responseSummary");
        assertThat(clearedSummary.isMissingNode() || clearedSummary.isNull()).isTrue();
        // The legacy query-parameter form still works.
        mockMvc.perform(auth(patch("/api/v1/dpa/dsar/" + id + "/status?status=COMPLETED&responseSummary=Sent"), token))
                .andExpect(status().isOk());
        mockMvc.perform(auth(patch("/api/v1/dpa/dsar/" + id + "/status?status=PENDING"), token))
                .andExpect(status().isConflict());
        JsonNode list = body(mockMvc.perform(auth(get("/api/v1/dpa/dsar"), token)).andExpect(status().isOk()).andReturn());
        assertThat(list.path("content").get(0).path("responseSummary").asText()).isEqualTo("Sent");
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private String registerAndLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "dpa+" + suffix + "@example.com";
        String ip = "10.7." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
        TenantRegisterRequest req = new TenantRegisterRequest();
        req.setOrganisationName("DPA Org " + suffix);
        req.setOrganisationContactEmail("ops+" + suffix + "@example.com");
        req.setAdminFirstName("Ama");
        req.setAdminLastName("Owner");
        req.setAdminEmail(email);
        req.setPassword("Password123");
        req.setCountry("GH");
        req.setTimezone("UTC");
        req.setIndustry("IT");
        MvcResult reg = mockMvc.perform(post("/api/v1/tenant/register").header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn();
        TenantRegisterResponse resp = objectMapper.readValue(reg.getResponse().getContentAsString(),
                TenantRegisterResponse.class);
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "Password123",
                                "organisationId", resp.getOrganisationId()))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString()).path("token").asText();
    }
}
