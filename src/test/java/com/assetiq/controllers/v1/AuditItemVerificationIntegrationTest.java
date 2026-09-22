package com.assetiq.controllers.v1;

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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Audit count sheets (V57): starting an audit generates its items, scanning or
 * typing an asset verifies one, and the audit only reads as fully verified when
 * every item is.
 */
@DisplayName("Audit item verification")
class AuditItemVerificationIntegrationTest extends BaseIntegrationTest {

    private static final String AUDITS = "/api/v1/audits";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String token;
    private String assetOneId;
    private String assetOneTag;
    private String assetTwoId;

    @BeforeEach
    void signIn() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        TenantRegisterRequest req = new TenantRegisterRequest();
        req.setOrganisationName("Audit Org " + suffix);
        req.setAdminFirstName("Abena");
        req.setAdminLastName("Auditor");
        req.setAdminEmail("audit+" + suffix + "@example.com");
        req.setPassword("Password123");
        req.setCountry("GH");
        MvcResult reg = mockMvc.perform(post("/api/v1/tenant/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn();
        TenantRegisterResponse resp = objectMapper.readValue(reg.getResponse().getContentAsString(),
                TenantRegisterResponse.class);
        JsonNode login = json(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", req.getAdminEmail(),
                                "password", "Password123", "organisationId", resp.getOrganisationId()))))
                .andExpect(status().isOk()).andReturn());
        token = login.path("token").asText();

        assetOneTag = "TAG-A-" + suffix;
        JsonNode one = json(send(post("/api/v1/assets"),
                Map.of("name", "Laptop A " + suffix, "assetTag", assetOneTag))
                .andExpect(status().is2xxSuccessful()).andReturn());
        assetOneId = one.path("id").asText();
        assertThat(one.path("assetTag").asText()).isEqualTo(assetOneTag);
        assetTwoId = json(send(post("/api/v1/assets"),
                Map.of("name", "Laptop B " + suffix, "assetTag", "TAG-B-" + suffix))
                .andExpect(status().is2xxSuccessful()).andReturn()).path("id").asText();
    }

    @Test
    void startingAnAuditGeneratesOneItemPerInScopeAsset() throws Exception {
        String auditId = newAudit();
        JsonNode started = start(auditId);

        assertThat(started.path("totalItemCount").asLong()).isEqualTo(2);
        assertThat(started.path("verifiedItemCount").asLong()).isZero();
        assertThat(started.path("allItemsVerified").asBoolean()).isFalse();

        JsonNode items = items(auditId, "");
        assertThat(items.path("total").asLong()).isEqualTo(2);
        assertThat(items.path("limit").asInt()).isEqualTo(20);
        assertThat(items.path("items")).hasSize(2);
        assertThat(items.path("items").get(0).path("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void generatingTwiceTopsUpRatherThanDoubles() throws Exception {
        String auditId = newAudit();
        start(auditId);
        JsonNode again = json(mockMvc.perform(auth(post(AUDITS + "/" + auditId + "/items/generate")))
                .andExpect(status().isOk()).andReturn());
        assertThat(again.path("totalItemCount").asLong()).isEqualTo(2);
    }

    @Test
    void anAuditWithNoItemsIsNotAVerifiedAudit() throws Exception {
        JsonNode planned = json(mockMvc.perform(auth(get(AUDITS + "/" + newAudit())))
                .andExpect(status().isOk()).andReturn());
        assertThat(planned.path("totalItemCount").asLong()).isZero();
        assertThat(planned.path("allItemsVerified").asBoolean()).isFalse();
    }

    @Test
    void verifyingEveryItemIsWhatMakesTheAuditVerified() throws Exception {
        String auditId = newAudit();
        start(auditId);

        // One by scan link, one by asset tag — the same endpoint serves both.
        JsonNode first = json(send(post(AUDITS + "/" + auditId + "/items/verify"),
                Map.of("scan", "https://app.example.com/scan?a=" + assetOneId, "condition", "Good"))
                .andExpect(status().isOk()).andReturn());
        assertThat(first.path("status").asText()).isEqualTo("VERIFIED");
        assertThat(first.path("assetId").asText()).isEqualTo(assetOneId);
        assertThat(first.path("verifiedAt").isMissingNode()).isFalse();
        assertThat(first.path("verifiedByName").asText()).isNotBlank();

        assertThat(audit(auditId).path("allItemsVerified").asBoolean()).isFalse();

        send(post(AUDITS + "/" + auditId + "/items/verify"), Map.of("scan", "asset:" + assetTwoId))
                .andExpect(status().isOk());

        JsonNode done = audit(auditId);
        assertThat(done.path("verifiedItemCount").asLong()).isEqualTo(2);
        assertThat(done.path("allItemsVerified").asBoolean()).isTrue();
    }

    @Test
    void aBareAssetTagVerifiesToo() throws Exception {
        String auditId = newAudit();
        start(auditId);
        JsonNode verified = json(send(post(AUDITS + "/" + auditId + "/items/verify"),
                Map.of("scan", assetOneTag)).andExpect(status().isOk()).andReturn());
        assertThat(verified.path("assetId").asText()).isEqualTo(assetOneId);
        assertThat(verified.path("status").asText()).isEqualTo("VERIFIED");
    }

    @Test
    void anUnknownCodeIsRejected() throws Exception {
        String auditId = newAudit();
        start(auditId);
        send(post(AUDITS + "/" + auditId + "/items/verify"), Map.of("scan", "asset:" + UUID.randomUUID()))
                .andExpect(status().isBadRequest());
        send(post(AUDITS + "/" + auditId + "/items/verify"), Map.of("scan", " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aDiscrepancyNeedsATypeAndAReasonAndMovesTheAudit() throws Exception {
        String auditId = newAudit();
        start(auditId);
        String itemId = items(auditId, "").path("items").get(0).path("id").asText();

        send(post(AUDITS + "/" + auditId + "/items/" + itemId + "/discrepancy"), Map.of("reason", "nowhere"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.discrepancyType").exists());
        send(post(AUDITS + "/" + auditId + "/items/" + itemId + "/discrepancy"),
                Map.of("discrepancyType", "MISSING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").exists());

        JsonNode flagged = json(send(post(AUDITS + "/" + auditId + "/items/" + itemId + "/discrepancy"),
                Map.of("discrepancyType", "MISSING", "reason", "Not at the desk it is booked to"))
                .andExpect(status().isOk()).andReturn());
        assertThat(flagged.path("status").asText()).isEqualTo("DISCREPANCY");
        assertThat(flagged.path("discrepancyFlag").asBoolean()).isTrue();
        assertThat(flagged.path("discrepancyType").asText()).isEqualTo("MISSING");

        JsonNode audit = audit(auditId);
        assertThat(audit.path("discrepancyCount").asLong()).isEqualTo(1);
        assertThat(audit.path("allItemsVerified").asBoolean()).isFalse();
        assertThat(audit.path("status").asText()).isEqualTo("DISCREPANCY_FOUND");
    }

    @Test
    void scanningAnAssetThatIsNotOnTheSheetIsAnUnexpectedFinding() throws Exception {
        String auditId = newAudit();
        start(auditId);
        // An asset created after the sheet was generated is not on it.
        String late = json(send(post("/api/v1/assets"), Map.of("name", "Late arrival " + UUID.randomUUID()))
                .andExpect(status().is2xxSuccessful()).andReturn()).path("id").asText();

        JsonNode recorded = json(send(post(AUDITS + "/" + auditId + "/items/verify"), Map.of("scan", late))
                .andExpect(status().isOk()).andReturn());
        assertThat(recorded.path("status").asText()).isEqualTo("DISCREPANCY");
        assertThat(recorded.path("discrepancyType").asText()).isEqualTo("UNEXPECTED");

        JsonNode audit = audit(auditId);
        assertThat(audit.path("totalItemCount").asLong()).isEqualTo(3);
        assertThat(audit.path("allItemsVerified").asBoolean()).isFalse();
    }

    @Test
    void itemsCanBeFilteredAndSearchedAndThePageSizeIsCapped() throws Exception {
        String auditId = newAudit();
        start(auditId);
        send(post(AUDITS + "/" + auditId + "/items/verify"), Map.of("scan", assetOneTag))
                .andExpect(status().isOk());

        assertThat(items(auditId, "?status=VERIFIED").path("total").asLong()).isEqualTo(1);
        assertThat(items(auditId, "?status=PENDING").path("total").asLong()).isEqualTo(1);
        assertThat(items(auditId, "?search=" + assetOneTag).path("total").asLong()).isEqualTo(1);
        assertThat(items(auditId, "?search=nothing-matches-this").path("total").asLong()).isZero();
        assertThat(items(auditId, "?size=5000").path("limit").asInt()).isEqualTo(200);
        assertThat(items(auditId, "?size=1&page=1").path("offset").asLong()).isEqualTo(1);
    }

    @Test
    void aClosedAuditsSheetIsFrozen() throws Exception {
        String auditId = newAudit();
        start(auditId);
        send(post(AUDITS + "/" + auditId + "/items/verify"), Map.of("scan", assetOneTag))
                .andExpect(status().isOk());
        send(post(AUDITS + "/" + auditId + "/items/verify"), Map.of("scan", "asset:" + assetTwoId))
                .andExpect(status().isOk());
        mockMvc.perform(auth(patch(AUDITS + "/" + auditId + "/status").param("status", "COMPLETED")))
                .andExpect(status().isOk());

        send(post(AUDITS + "/" + auditId + "/items/verify"), Map.of("scan", assetOneTag))
                .andExpect(status().isConflict());
        mockMvc.perform(auth(post(AUDITS + "/" + auditId + "/items/generate")))
                .andExpect(status().isConflict());
        // Reading the closed audit's sheet still works.
        assertThat(items(auditId, "").path("total").asLong()).isEqualTo(2);
    }

    @Test
    void anotherTenantsAuditIsNotReachable() throws Exception {
        mockMvc.perform(auth(get(AUDITS + "/" + UUID.randomUUID() + "/items")))
                .andExpect(status().isBadRequest());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String newAudit() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("auditDate", LocalDate.now().toString());
        return json(send(post(AUDITS), body).andExpect(status().isCreated()).andReturn()).path("id").asText();
    }

    private JsonNode start(String auditId) throws Exception {
        return json(mockMvc.perform(auth(patch(AUDITS + "/" + auditId + "/status")
                        .param("status", "IN_PROGRESS")))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode audit(String auditId) throws Exception {
        return json(mockMvc.perform(auth(get(AUDITS + "/" + auditId))).andExpect(status().isOk()).andReturn());
    }

    private JsonNode items(String auditId, String query) throws Exception {
        return json(mockMvc.perform(auth(get(AUDITS + "/" + auditId + "/items" + query)))
                .andExpect(status().isOk()).andReturn());
    }

    private ResultActions send(MockHttpServletRequestBuilder builder, Map<String, Object> body) throws Exception {
        return mockMvc.perform(auth(builder).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
