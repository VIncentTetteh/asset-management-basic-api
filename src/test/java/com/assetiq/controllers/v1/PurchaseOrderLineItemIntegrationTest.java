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

import java.util.HashMap;
import java.util.List;
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
 * Purchase order line items (V56): lines are written with the order, the stored
 * total is derived from them, and they can only change while the order is DRAFT.
 */
@DisplayName("Purchase order line items")
class PurchaseOrderLineItemIntegrationTest extends BaseIntegrationTest {

    private static final String BASE = "/api/v1/purchase-orders";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String token;
    private String departmentId;
    private String supplierId;
    private String categoryId;
    private String poNumber;

    @BeforeEach
    void signIn() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        poNumber = "PO-" + suffix;
        TenantRegisterRequest req = new TenantRegisterRequest();
        req.setOrganisationName("PO Lines Org " + suffix);
        req.setAdminFirstName("Kofi");
        req.setAdminLastName("Buyer");
        req.setAdminEmail("po+" + suffix + "@example.com");
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

        departmentId = created("/api/v1/departments", Map.of("name", "Procurement " + suffix));
        supplierId = created("/api/v1/suppliers", Map.of("name", "Acme " + suffix));
        categoryId = created("/api/v1/categories", Map.of("name", "Laptops " + suffix));
    }

    @Test
    void linesAreCreatedWithTheOrderAndDriveItsTotal() throws Exception {
        JsonNode created = json(send(post(BASE), orderWith(List.of(
                lineOf("Dell Latitude 5450", "2", "1200.00", "12.5", categoryId),
                lineOf("USB-C dock", "2", "150.00", null, null))))
                .andExpect(status().isCreated()).andReturn());

        assertThat(created.path("lineItems")).hasSize(2);
        assertThat(created.path("lineItems").get(0).path("lineNumber").asInt()).isEqualTo(1);
        assertThat(created.path("lineItems").get(0).path("description").asText())
                .isEqualTo("Dell Latitude 5450");
        assertThat(created.path("lineItems").get(0).path("categoryId").asText()).isEqualTo(categoryId);
        assertThat(created.path("lineItems").get(0).path("lineTotal").decimalValue())
                .isEqualByComparingTo("2700.0000");
        assertThat(created.path("lineItems").get(1).path("lineTotal").decimalValue())
                .isEqualByComparingTo("300.0000");
        // The supplied total (1.00) is ignored: the lines are the source of truth.
        assertThat(created.path("totalAmount").decimalValue()).isEqualByComparingTo("3000.00");

        JsonNode fetched = json(mockMvc.perform(auth(get(BASE + "/" + created.path("id").asText())))
                .andExpect(status().isOk()).andReturn());
        assertThat(fetched.path("lineItems")).hasSize(2);
        assertThat(fetched.path("totalAmount").decimalValue()).isEqualByComparingTo("3000.00");
    }

    @Test
    void anOrderWithNoLinesKeepsItsLumpSum() throws Exception {
        JsonNode created = json(send(post(BASE), orderWith(null))
                .andExpect(status().isCreated()).andReturn());
        assertThat(created.path("lineItems")).isEmpty();
        assertThat(created.path("totalAmount").decimalValue()).isEqualByComparingTo("1.00");
    }

    @Test
    void putReplacesTheWholeSetAndRenumbersIt() throws Exception {
        String id = json(send(post(BASE), orderWith(List.of(
                lineOf("First", "1", "10.00", null, null),
                lineOf("Second", "1", "20.00", null, null),
                lineOf("Third", "1", "30.00", null, null))))
                .andExpect(status().isCreated()).andReturn()).path("id").asText();

        JsonNode replaced = json(send(put(BASE + "/" + id), orderWith(List.of(
                lineOf("Only line", "4", "25.00", null, null))))
                .andExpect(status().isOk()).andReturn());
        assertThat(replaced.path("lineItems")).hasSize(1);
        assertThat(replaced.path("lineItems").get(0).path("lineNumber").asInt()).isEqualTo(1);
        assertThat(replaced.path("lineItems").get(0).path("description").asText()).isEqualTo("Only line");
        assertThat(replaced.path("totalAmount").decimalValue()).isEqualByComparingTo("100.00");
    }

    @Test
    void putWithoutLinesClearsThemAndPatchWithoutLinesLeavesThem() throws Exception {
        String id = json(send(post(BASE), orderWith(List.of(lineOf("Keep me", "1", "10.00", null, null))))
                .andExpect(status().isCreated()).andReturn()).path("id").asText();

        JsonNode patched = json(send(patch(BASE + "/" + id), Map.of("remarks", "still itemised"))
                .andExpect(status().isOk()).andReturn());
        assertThat(patched.path("lineItems")).hasSize(1);

        JsonNode cleared = json(send(put(BASE + "/" + id), orderWith(null))
                .andExpect(status().isOk()).andReturn());
        assertThat(cleared.path("lineItems")).isEmpty();
        // With no lines the order is a lump sum again, so the supplied total stands.
        assertThat(cleared.path("totalAmount").decimalValue()).isEqualByComparingTo("1.00");
    }

    @Test
    void patchWithAnEmptyListClearsTheLines() throws Exception {
        String id = json(send(post(BASE), orderWith(List.of(lineOf("Gone", "1", "10.00", null, null))))
                .andExpect(status().isCreated()).andReturn()).path("id").asText();
        JsonNode cleared = json(send(patch(BASE + "/" + id), Map.of("lineItems", List.of()))
                .andExpect(status().isOk()).andReturn());
        assertThat(cleared.path("lineItems")).isEmpty();
    }

    @Test
    void linesCannotBeEditedOnceTheOrderHasLeftDraft() throws Exception {
        String id = json(send(post(BASE), orderWith(List.of(lineOf("Locked", "1", "10.00", null, null))))
                .andExpect(status().isCreated()).andReturn()).path("id").asText();
        mockMvc.perform(auth(post(BASE + "/" + id + "/submit"))).andExpect(status().isOk());

        send(put(BASE + "/" + id), orderWith(List.of(lineOf("Sneaky", "1", "9999.00", null, null))))
                .andExpect(status().isConflict());
        send(patch(BASE + "/" + id), Map.of("lineItems", List.of(lineOf("Sneaky", "1", "9999.00", null, null))))
                .andExpect(status().isConflict());

        JsonNode unchanged = json(mockMvc.perform(auth(get(BASE + "/" + id)))
                .andExpect(status().isOk()).andReturn());
        assertThat(unchanged.path("lineItems").get(0).path("description").asText()).isEqualTo("Locked");
        assertThat(unchanged.path("totalAmount").decimalValue()).isEqualByComparingTo("10.00");
    }

    @Test
    void aBlankDescriptionIsAFieldError() throws Exception {
        send(post(BASE), orderWith(List.of(lineOf("   ", "1", "10.00", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void aDescriptionLongerThanTheColumnIsAFieldError() throws Exception {
        send(post(BASE), orderWith(List.of(lineOf("x".repeat(501), "1", "10.00", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void zeroOrNegativeQuantityIsAFieldError() throws Exception {
        send(post(BASE), orderWith(List.of(lineOf("Nothing", "0", "10.00", null, null))))
                .andExpect(status().isBadRequest());
        send(post(BASE), orderWith(List.of(lineOf("Negative", "-1", "10.00", null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aNegativeUnitPriceIsAFieldErrorButZeroIsAllowed() throws Exception {
        send(post(BASE), orderWith(List.of(lineOf("Refund", "1", "-1.00", null, null))))
                .andExpect(status().isBadRequest());
        send(post(BASE), orderWith(List.of(lineOf("Free sample", "1", "0", null, null))))
                .andExpect(status().isCreated());
    }

    @Test
    void aCategoryFromAnotherTenantIsRejected() throws Exception {
        send(post(BASE), orderWith(List.of(
                lineOf("Foreign category", "1", "10.00", null, UUID.randomUUID().toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingTheOrderTakesItsLinesWithIt() throws Exception {
        String id = json(send(post(BASE), orderWith(List.of(lineOf("Doomed", "1", "10.00", null, null))))
                .andExpect(status().isCreated()).andReturn()).path("id").asText();
        mockMvc.perform(auth(delete(BASE + "/" + id))).andExpect(status().isNoContent());
        // "not found" is a 400 BAD_REQUEST in this codebase (IllegalArgumentException).
        mockMvc.perform(auth(get(BASE + "/" + id))).andExpect(status().isBadRequest());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> orderWith(List<Map<String, Object>> lines) {
        Map<String, Object> body = new HashMap<>();
        body.put("poNumber", poNumber);
        body.put("totalAmount", "1.00");
        body.put("departmentId", departmentId);
        body.put("supplierId", supplierId);
        if (lines != null) {
            body.put("lineItems", lines);
        }
        return body;
    }

    private static Map<String, Object> lineOf(String description, String quantity, String unitPrice,
                                              String taxRate, String categoryId) {
        Map<String, Object> line = new HashMap<>();
        line.put("description", description);
        line.put("quantity", quantity);
        line.put("unitPrice", unitPrice);
        if (taxRate != null) line.put("taxRate", taxRate);
        if (categoryId != null) line.put("categoryId", categoryId);
        return line;
    }

    private String created(String path, Map<String, Object> body) throws Exception {
        return json(send(post(path), body).andExpect(status().is2xxSuccessful()).andReturn()).path("id").asText();
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
