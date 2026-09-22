package com.assetiq.compliance;

import com.assetiq.BaseIntegrationTest;
import com.assetiq.dto.TenantRegisterRequest;
import com.assetiq.dto.TenantRegisterResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Compliance registers: PUT clears what the form cleared, natural keys are DUPLICATE
 * field errors, owners are users of the tenant, and actor fields are stamped by the server.
 */
@DisplayName("Compliance edits (PUT full replace, duplicates, owners, server-stamped actors)")
class ComplianceEditIntegrationTest extends BaseIntegrationTest {

    private static final String BASE = "/api/v1/compliance";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String token;
    private String email;
    private String userId;

    @BeforeEach
    void signIn() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        email = "comp+" + suffix + "@example.com";
        TenantRegisterRequest req = new TenantRegisterRequest();
        req.setOrganisationName("Compliance Org " + suffix);
        req.setAdminFirstName("Ama");
        req.setAdminLastName("Owner");
        req.setAdminEmail(email);
        req.setPassword("Password123");
        req.setCountry("GH");
        MvcResult reg = mockMvc.perform(post("/api/v1/tenant/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn();
        TenantRegisterResponse resp = objectMapper.readValue(reg.getResponse().getContentAsString(),
                TenantRegisterResponse.class);
        JsonNode login = json(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "Password123",
                                "organisationId", resp.getOrganisationId()))))
                .andExpect(status().isOk()).andReturn());
        token = login.path("token").asText();
        userId = login.path("user").path("id").asText();
    }

    @Test
    void putClearsOptionalFields_patchLeavesThem() throws Exception {
        Map<String, Object> risk = new HashMap<>(Map.of("title", "Ransomware", "likelihood", 3, "impact", 4,
                "residualRisk", 6, "reviewDate", "2026-10-01T12:00:00Z", "ownerId", userId));
        String id = json(send(post(BASE + "/risks"), risk).andExpect(status().isCreated()).andReturn()).path("id").asText();

        // PATCH without the fields keeps them.
        JsonNode patched = json(send(patch(BASE + "/risks/" + id), Map.of("title", "Ransomware 2"))
                .andExpect(status().isOk()).andReturn());
        assertThat(patched.path("residualRisk").asInt()).isEqualTo(6);
        assertThat(patched.path("ownerId").asText()).isEqualTo(userId);

        // PUT without them clears date, number and owner.
        JsonNode replaced = json(send(put(BASE + "/risks/" + id),
                Map.of("title", "Ransomware 2", "likelihood", 3, "impact", 4))
                .andExpect(status().isOk()).andReturn());
        assertThat(absent(replaced.path("residualRisk"))).isTrue();
        assertThat(absent(replaced.path("reviewDate"))).isTrue();
        assertThat(absent(replaced.path("ownerId"))).isTrue();
    }

    @Test
    void outOfRangeValuesAreFieldErrorsOnPut() throws Exception {
        String id = json(send(post(BASE + "/risks"), Map.of("title", "R", "likelihood", 1, "impact", 1))
                .andExpect(status().isCreated()).andReturn()).path("id").asText();
        send(put(BASE + "/risks/" + id), Map.of("title", "R", "likelihood", 1, "impact", 1, "residualRisk", 26))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.residualRisk").exists());
    }

    @Test
    void naturalKeyDuplicatesAreFieldErrors() throws Exception {
        Map<String, Object> sla = Map.of("month", 3, "year", 2026, "uptimePercent", 99.9);
        send(post(BASE + "/sla-metrics"), sla).andExpect(status().isCreated());
        send(post(BASE + "/sla-metrics"), sla)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errors.month").exists());
        String other = json(send(post(BASE + "/sla-metrics"), Map.of("month", 4, "year", 2026, "uptimePercent", 99.0))
                .andExpect(status().isCreated()).andReturn()).path("id").asText();
        send(patch(BASE + "/sla-metrics/" + other), Map.of("month", 3))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errors.month").exists());

        send(post(BASE + "/pci-saq"), Map.of("requirementNumber", "1.1")).andExpect(status().isCreated());
        send(post(BASE + "/pci-saq"), Map.of("requirementNumber", "1.1"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errors.requirementNumber").exists());

        send(post(BASE + "/bog-controls"), Map.of("directiveRef", "BoG/ICT/1", "requirement", "x"))
                .andExpect(status().isCreated());
        send(post(BASE + "/bog-controls"), Map.of("directiveRef", "BoG/ICT/1", "requirement", "y"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errors.directiveRef").exists());
    }

    @Test
    void slaMetricCanBeDeletedAndItsPeriodReused() throws Exception {
        // There was no delete at all, so a wrong figure occupied its month forever.
        String id = json(send(post(BASE + "/sla-metrics"), Map.of("month", 7, "year", 2031, "uptimePercent", 99.9))
                .andExpect(status().isCreated()).andReturn()).path("id").asText();

        mockMvc.perform(auth(delete(BASE + "/sla-metrics/" + id))).andExpect(status().isNoContent());
        mockMvc.perform(auth(get(BASE + "/sla-metrics/" + id))).andExpect(status().isBadRequest());

        // The unique index covers live rows only, so the month is free again.
        send(post(BASE + "/sla-metrics"), Map.of("month", 7, "year", 2031, "uptimePercent", 98.0))
                .andExpect(status().isCreated());
    }

    @Test
    void ownersMustBeTenantUsers_andActorsAreStampedByTheServer() throws Exception {
        send(post(BASE + "/controls"), Map.of("framework", "ISO_27001", "controlRef", "A.5.1",
                "controlName", "Policies", "ownerId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.ownerId").exists());

        JsonNode control = json(send(post(BASE + "/controls"), Map.of("framework", "ISO_27001", "controlRef", "A.5.2",
                "controlName", "Roles", "lastReviewedAt", "2026-09-01T12:00:00Z", "lastReviewedByEmail", "spoof@x.com"))
                .andExpect(status().isCreated()).andReturn());
        assertThat(control.path("lastReviewedByEmail").asText()).isEqualTo(email);

        send(post(BASE + "/policies"), Map.of("title", "Access policy", "approvedByEmail", "stranger@example.com"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.approvedByEmail").exists());
        JsonNode policy = json(send(post(BASE + "/policies"), Map.of("title", "Access policy", "approvedByEmail", email))
                .andExpect(status().isCreated()).andReturn());
        assertThat(policy.path("approvedByEmail").asText()).isEqualTo(email);
    }

    @Test
    void zoneAssetCountIsDerived() throws Exception {
        JsonNode zone = json(send(post(BASE + "/security-zones"), Map.of("name", "DMZ", "purdueLevel", 3, "assetCount", 42))
                .andExpect(status().isCreated()).andReturn());
        assertThat(zone.path("assetCount").asInt()).isZero();
        JsonNode listed = json(mockMvc.perform(auth(get(BASE + "/security-zones"))).andExpect(status().isOk()).andReturn());
        assertThat(listed.get(0).path("assetCount").asInt()).isZero();
    }

    @Test
    void incidentTimelineAndCompensatingControlRules() throws Exception {
        send(post(BASE + "/incidents"), Map.of("title", "Phish", "severity", "P3_MEDIUM",
                "detectedAt", "2026-09-10T12:00:00Z", "resolvedAt", "2026-09-01T12:00:00Z"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.resolvedAt").exists());
        JsonNode resolved = json(send(post(BASE + "/incidents"), Map.of("title", "Phish", "severity", "P3_MEDIUM",
                "status", "RESOLVED")).andExpect(status().isCreated()).andReturn());
        assertThat(resolved.path("resolvedAt").asText()).isNotBlank();

        send(post(BASE + "/pci-saq"), Map.of("requirementNumber", "8.3", "complianceStatus", "COMPENSATING_CONTROL"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.compensatingControl").exists());
    }

    private ResultActions send(MockHttpServletRequestBuilder builder, Map<String, Object> body) throws Exception {
        return mockMvc.perform(auth(builder).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private static boolean absent(JsonNode node) {
        return node.isMissingNode() || node.isNull();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
