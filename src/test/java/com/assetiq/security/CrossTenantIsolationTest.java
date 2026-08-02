package com.assetiq.security;

import com.assetiq.dto.TenantRegisterRequest;
import com.assetiq.dto.TenantRegisterResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cross-tenant isolation suite.
 *
 * <p>AssetIQ is a multi-tenant SaaS: every organisation's data lives in shared
 * tables, and separation is enforced in application code rather than by Postgres
 * row-level security or a Hibernate tenant filter. That makes isolation a property
 * each service must uphold by hand, which is exactly the kind of property that
 * rots silently. These tests are the regression net.
 *
 * <p>The shape is always the same: register two unrelated tenants, create a
 * resource inside org A, then attack it with org B's token and assert that org B
 * neither reads nor mutates it.
 *
 * <p><b>On assertions:</b> we deliberately do not pin an exact status code.
 * Different layers express denial differently — {@code @PreAuthorize} yields 403,
 * a tenant-scoped lookup that finds nothing yields 404 — and both are correct
 * outcomes. The security property is "org B does not obtain org A's data", so we
 * assert non-2xx <em>and</em> that org A's distinguishing values are absent from
 * the response body. A 200 carrying another tenant's record is the failure we care
 * about; whether a denial is 403 or 404 is a design detail, not a security one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Cross-tenant isolation")
class CrossTenantIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Rate limiting keys off the client address; vary it so the suite isn't throttled. */
    private final AtomicInteger clientCounter = new AtomicInteger(1);

    private Tenant orgA;
    private Tenant orgB;

    /** A registered organisation plus the admin token that speaks for it. */
    private record Tenant(UUID organisationId, String token, String suffix) {}

    @BeforeEach
    void registerBothTenants() throws Exception {
        // Registered once per class: tenant registration is rate-limited and slow,
        // and every test here is read-only with respect to the other tenant's data.
        if (orgA != null && orgB != null) {
            return;
        }
        orgA = registerTenant("Alpha");
        orgB = registerTenant("Beta");
    }

    // ── Assets ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("org B cannot read org A's asset by id")
    void assetGetById_deniedAcrossTenants() throws Exception {
        String name = "Alpha Server " + orgA.suffix();
        UUID assetId = createAsset(orgA, name);

        // Sanity: the owner can read it. Without this the test could pass because
        // the resource never existed.
        assertReadable("/api/v1/assets/" + assetId, orgA, name);

        assertNotReadable("/api/v1/assets/" + assetId, orgB, name);
    }

    @Test
    @DisplayName("org B cannot update org A's asset")
    void assetUpdate_deniedAcrossTenants() throws Exception {
        String name = "Alpha Router " + orgA.suffix();
        UUID assetId = createAsset(orgA, name);

        mockMvc.perform(auth(put("/api/v1/assets/" + assetId), orgB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Hijacked"))))
                .andExpect(status().is4xxClientError());

        // The write must not have landed — check through the legitimate owner.
        assertReadable("/api/v1/assets/" + assetId, orgA, name);
    }

    @Test
    @DisplayName("org B cannot delete org A's asset")
    void assetDelete_deniedAcrossTenants() throws Exception {
        String name = "Alpha Switch " + orgA.suffix();
        UUID assetId = createAsset(orgA, name);

        mockMvc.perform(auth(delete("/api/v1/assets/" + assetId), orgB))
                .andExpect(status().is4xxClientError());

        // Still there for its owner. A soft-delete that succeeded would fail this.
        assertReadable("/api/v1/assets/" + assetId, orgA, name);
    }

    @Test
    @DisplayName("the asset register never lists another tenant's assets")
    void assetList_doesNotLeakAcrossTenants() throws Exception {
        String name = "Alpha Listed " + orgA.suffix();
        createAsset(orgA, name);

        assertListExcludes("/api/v1/assets?limit=200", orgB, name);
    }

    @Test
    @DisplayName("asset stats count only the caller's own tenant")
    void assetStats_scopedToCallerTenant() throws Exception {
        long before = assetStatsTotal(orgB);

        createAsset(orgA, "Alpha Uncounted 1 " + orgA.suffix());
        createAsset(orgA, "Alpha Uncounted 2 " + orgA.suffix());

        // Aggregates are a classic isolation blind spot: the row-level read can be
        // scoped correctly while a COUNT(*) quietly spans every tenant.
        assertThat(assetStatsTotal(orgB))
                .as("org A's new assets must not move org B's totals")
                .isEqualTo(before);
    }

    // ── Reference data (category / location / supplier) ───────────────────────

    @Test
    @DisplayName("org B cannot read org A's category by id")
    void categoryGetById_deniedAcrossTenants() throws Exception {
        String name = "Alpha Category " + orgA.suffix();
        UUID id = createSimple("/api/v1/categories", orgA, name);

        assertReadable("/api/v1/categories/" + id, orgA, name);
        assertNotReadable("/api/v1/categories/" + id, orgB, name);
    }

    @Test
    @DisplayName("the category list never leaks another tenant's categories")
    void categoryList_doesNotLeakAcrossTenants() throws Exception {
        String name = "Alpha Cat Listed " + orgA.suffix();
        createSimple("/api/v1/categories", orgA, name);

        assertListExcludes("/api/v1/categories", orgB, name);
    }

    @Test
    @DisplayName("org B cannot read org A's location by id")
    void locationGetById_deniedAcrossTenants() throws Exception {
        String name = "Alpha Location " + orgA.suffix();
        UUID id = createSimple("/api/v1/locations", orgA, name);

        assertReadable("/api/v1/locations/" + id, orgA, name);
        assertNotReadable("/api/v1/locations/" + id, orgB, name);
    }

    @Test
    @DisplayName("the location list never leaks another tenant's locations")
    void locationList_doesNotLeakAcrossTenants() throws Exception {
        String name = "Alpha Loc Listed " + orgA.suffix();
        createSimple("/api/v1/locations", orgA, name);

        assertListExcludes("/api/v1/locations", orgB, name);
    }

    @Test
    @DisplayName("org B cannot read org A's supplier by id")
    void supplierGetById_deniedAcrossTenants() throws Exception {
        String name = "Alpha Supplier " + orgA.suffix();
        UUID id = createSimple("/api/v1/suppliers", orgA, name);

        assertReadable("/api/v1/suppliers/" + id, orgA, name);
        assertNotReadable("/api/v1/suppliers/" + id, orgB, name);
    }

    @Test
    @DisplayName("supplier lookup by email does not cross tenants")
    void supplierByEmail_deniedAcrossTenants() throws Exception {
        String name = "Alpha Emailed Supplier " + orgA.suffix();
        String email = "supplier+" + orgA.suffix() + "@alpha.example.com";

        createJson("/api/v1/suppliers", orgA, Map.of("name", name, "email", email));

        // A by-natural-key lookup is easy to write as a global findByEmail().
        assertNotReadable("/api/v1/suppliers/by-email?email=" + email, orgB, name);
    }

    // ── Employees (personal data) ─────────────────────────────────────────────

    @Test
    @DisplayName("org B cannot read org A's employee by id")
    void employeeGetById_deniedAcrossTenants() throws Exception {
        String lastName = "Alphaperson" + orgA.suffix();
        UUID id = createJson("/api/v1/employees", orgA,
                Map.of("firstName", "Ama", "lastName", lastName));

        assertReadable("/api/v1/employees/" + id, orgA, lastName);
        assertNotReadable("/api/v1/employees/" + id, orgB, lastName);
    }

    @Test
    @DisplayName("the employee register never leaks another tenant's people")
    void employeeList_doesNotLeakAcrossTenants() throws Exception {
        String lastName = "Alphalisted" + orgA.suffix();
        createJson("/api/v1/employees", orgA,
                Map.of("firstName", "Kofi", "lastName", lastName));

        assertListExcludes("/api/v1/employees?size=200", orgB, lastName);
    }

    @Test
    @DisplayName("org B cannot read the assets checked out to org A's employee")
    void employeeAssets_deniedAcrossTenants() throws Exception {
        String lastName = "Alphaholder" + orgA.suffix();
        UUID id = createJson("/api/v1/employees", orgA,
                Map.of("firstName", "Yaa", "lastName", lastName));

        mockMvc.perform(auth(get("/api/v1/employees/" + id + "/assets"), orgB))
                .andExpect(status().is4xxClientError());
    }

    // ── Everything else with a get-by-id ──────────────────────────────────────

    /**
     * The rest of the resources that expose {@code GET /{id}}.
     *
     * <p>Driven from a table rather than hand-written per resource, because the
     * interesting property is uniformity: isolation here is enforced service by
     * service, so the resource nobody thought to check is exactly the one that leaks.
     * Adding a row is cheap enough that there is no excuse for a new endpoint to go
     * uncovered.
     *
     * <p>Each entry is a path, a create payload, the value that must never appear in
     * org B's responses, and a query suffix for the handful of endpoints that demand
     * extra parameters on create. {@code {orgId}} is substituted with the creating
     * tenant's organisation — depreciation policies require it as a request parameter
     * even though the tenant is already established by the JWT.
     */
    static Stream<Arguments> tenantScopedResources() {
        String s = UUID.randomUUID().toString().substring(0, 8);
        return Stream.of(
                Arguments.of("departments", "/api/v1/departments",
                        Map.<String, Object>of("name", "Alpha Dept " + s), "Alpha Dept " + s, ""),

                Arguments.of("depreciation policies", "/api/v1/depreciation-policies",
                        Map.<String, Object>of(
                                "name", "Alpha Policy " + s,
                                "method", "STRAIGHT_LINE",
                                "usefulLifeMonths", 60,
                                "salvageValuePercent", 10),
                        "Alpha Policy " + s, "?organisationId={orgId}"),

                // Webhooks hand back an HMAC signing secret on create. A cross-tenant read
                // would let another tenant forge signed deliveries, so this one matters
                // well beyond the usual "they can see our data".
                Arguments.of("webhooks", "/api/v1/webhooks",
                        Map.<String, Object>of(
                                "name", "Alpha Hook " + s,
                                "url", "https://alpha-" + s + ".example.com/hook",
                                "events", List.of("test.webhook"),
                                "active", true),
                        "Alpha Hook " + s, ""),

                // Software licences store licence keys — vendor credentials in all but name.
                Arguments.of("software licences", "/api/v1/licenses",
                        Map.<String, Object>of(
                                "name", "Alpha Licence " + s,
                                "vendor", "Alpha Vendor",
                                "licenseKey", "ALPHA-KEY-" + s,
                                "licenseType", "SUBSCRIPTION",
                                "status", "ACTIVE"),
                        "ALPHA-KEY-" + s, ""),

                Arguments.of("budgets", "/api/v1/budgets",
                        Map.<String, Object>of(
                                "name", "Alpha Budget " + s,
                                "totalAmount", 50000,
                                "periodStart", "2026-01-01",
                                "periodEnd", "2026-12-31",
                                "status", "DRAFT"),
                        "Alpha Budget " + s, ""),

                Arguments.of("contracts", "/api/v1/contracts",
                        Map.<String, Object>of(
                                "title", "Alpha Contract " + s,
                                "contractNumber", "ALPHA-CTR-" + s,
                                "contractType", "SERVICE_LEVEL_AGREEMENT",
                                "status", "DRAFT",
                                "startDate", "2026-01-01",
                                "endDate", "2026-12-31",
                                "alertDaysBefore", 30),
                        "ALPHA-CTR-" + s, ""));
    }

    @ParameterizedTest(name = "org B cannot read org A''s {0}")
    @MethodSource("tenantScopedResources")
    @DisplayName("get-by-id is tenant-scoped across the remaining resources")
    void getById_deniedAcrossTenants(String label, String path, Map<String, Object> payload,
                                     String marker, String createQuery) throws Exception {
        UUID id = createJson(path + resolve(createQuery), orgA, payload);

        assertReadable(path + "/" + id, orgA, marker);
        assertNotReadable(path + "/" + id, orgB, marker);
    }

    @ParameterizedTest(name = "the {0} list never leaks across tenants")
    @MethodSource("tenantScopedResources")
    @DisplayName("list endpoints are tenant-scoped across the remaining resources")
    void list_doesNotLeakAcrossTenants(String label, String path, Map<String, Object> payload,
                                       String marker, String createQuery) throws Exception {
        createJson(path + resolve(createQuery), orgA, payload);

        assertListExcludes(path, orgB, marker);
    }

    // ── Generated artefacts ───────────────────────────────────────────────────

    @Test
    @DisplayName("org B cannot download a report generated by org A")
    void reportDownload_deniedAcrossTenants() throws Exception {
        createAsset(orgA, "Alpha Reported " + orgA.suffix());

        MvcResult created = mockMvc.perform(auth(post("/api/v1/reports/assets"), orgA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("format", "CSV"))))
                .andExpect(status().isCreated())
                .andReturn();

        String downloadUrl = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("downloadUrl").asText();

        // A generated export is the highest-value object in the system: one file
        // holding the whole asset register.
        mockMvc.perform(auth(get(downloadUrl), orgB))
                .andExpect(status().is4xxClientError());
    }

    // ── Tenant context plumbing ───────────────────────────────────────────────

    @Test
    @DisplayName("a spoofed X-Organisation-Id header cannot override the JWT's tenant claim")
    void spoofedOrganisationHeader_isIgnored() throws Exception {
        String name = "Alpha Header Target " + orgA.suffix();
        UUID assetId = createAsset(orgA, name);

        // TenantFilter must trust the signed JWT claim over any client-supplied
        // header. If the header ever wins, every tenant can read every other.
        mockMvc.perform(auth(get("/api/v1/assets/" + assetId), orgB)
                        .header("X-Organisation-Id", orgA.organisationId().toString()))
                .andExpect(status().is4xxClientError());

        assertListExcludes("/api/v1/assets?limit=200", orgB, name);
    }

    @Test
    @DisplayName("an unauthenticated caller cannot read tenant data with only a header")
    void unauthenticatedWithHeader_isRejected() throws Exception {
        String name = "Alpha Anon Target " + orgA.suffix();
        UUID assetId = createAsset(orgA, name);

        mockMvc.perform(get("/api/v1/assets/" + assetId)
                        .header("X-Organisation-Id", orgA.organisationId().toString())
                        .header("X-Forwarded-For", nextClient()))
                .andExpect(status().is4xxClientError());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Tenant registerTenant(String label) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        TenantRegisterRequest req = new TenantRegisterRequest();
        req.setOrganisationName(label + " Isolation Org " + suffix);
        req.setOrganisationContactEmail("ops+" + suffix + "@example.com");
        req.setAdminFirstName(label);
        req.setAdminLastName("Admin");
        req.setAdminEmail("admin+" + suffix + "@example.com");
        req.setPassword("Password123");
        req.setCountry("GH");
        req.setTimezone("UTC");
        req.setIndustry("IT");

        MvcResult result = mockMvc.perform(post("/api/v1/tenant/register")
                        .header("X-Forwarded-For", nextClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        TenantRegisterResponse resp = objectMapper.readValue(
                result.getResponse().getContentAsString(), TenantRegisterResponse.class);

        return new Tenant(resp.getOrganisationId(), resp.getToken(), suffix);
    }

    /** Fills the {orgId} placeholder used by create endpoints that demand it as a parameter. */
    private String resolve(String queryTemplate) {
        return queryTemplate.replace("{orgId}", orgA.organisationId().toString());
    }

    private String nextClient() {
        return "10.0.0." + clientCounter.getAndIncrement();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, Tenant tenant) {
        String client = nextClient();
        return builder.header("Authorization", "Bearer " + tenant.token())
                .header("X-Client-ID", client)
                .header("X-Forwarded-For", client);
    }

    private UUID createAsset(Tenant tenant, String name) throws Exception {
        return createJson("/api/v1/assets", tenant, Map.of("name", name));
    }

    private UUID createSimple(String path, Tenant tenant, String name) throws Exception {
        return createJson(path, tenant, Map.of("name", name));
    }

    private UUID createJson(String path, Tenant tenant, Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(auth(post(path), tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(node.get("id").asText());
    }

    private long assetStatsTotal(Tenant tenant) throws Exception {
        MvcResult result = mockMvc.perform(auth(get("/api/v1/assets/stats"), tenant))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("total").asLong();
    }

    /** Confirms the owning tenant really can see the resource, so denial tests mean something. */
    private void assertReadable(String path, Tenant owner, String marker) throws Exception {
        MvcResult result = mockMvc.perform(auth(get(path), owner))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .as("owning tenant should see its own resource at %s", path)
                .contains(marker);
    }

    private void assertNotReadable(String path, Tenant intruder, String marker) throws Exception {
        MvcResult result = mockMvc.perform(auth(get(path), intruder)).andReturn();
        int status = result.getResponse().getStatus();
        String body = result.getResponse().getContentAsString();

        assertThat(status)
                .as("GET %s from a foreign tenant must not succeed (body: %s)", path, body)
                .isGreaterThanOrEqualTo(400);
        assertThat(body)
                .as("GET %s must not leak the owning tenant's data", path)
                .doesNotContain(marker);
    }

    private void assertListExcludes(String path, Tenant intruder, String marker) throws Exception {
        MvcResult result = mockMvc.perform(auth(get(path), intruder)).andReturn();
        int status = result.getResponse().getStatus();

        // A denied list is fine; a list that succeeds and contains foreign rows is not.
        if (status < 400) {
            assertThat(result.getResponse().getContentAsString())
                    .as("%s must not include another tenant's records", path)
                    .doesNotContain(marker);
        }
    }
}
