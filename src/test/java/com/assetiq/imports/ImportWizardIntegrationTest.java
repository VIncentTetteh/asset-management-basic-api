package com.assetiq.imports;

import com.assetiq.dto.TenantRegisterRequest;
import com.assetiq.dto.TenantRegisterResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The wizard end to end, against a real database.
 *
 * <p>Two things this suite exists for that a unit test cannot show. First, that the five
 * steps actually join up: a file uploaded in one request is still there two requests
 * later, because it went to the storage service rather than to a field on a bean.
 * Second, that a staged upload and a saved mapping belong to exactly one organisation —
 * holding another tenant's id must get you nothing.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Import wizard")
class ImportWizardIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private final AtomicInteger clientCounter = new AtomicInteger(1);

    private Tenant orgA;
    private Tenant orgB;

    private record Tenant(UUID organisationId, String token, String suffix) {}

    @BeforeEach
    void registerTenants() throws Exception {
        if (orgA != null && orgB != null) return;
        orgA = registerTenant("Import Alpha");
        orgB = registerTenant("Import Beta");
    }

    // ── Discovery and templates ───────────────────────────────────────────────

    @Test
    @DisplayName("every wired entity type is listed with the slug the web wizard uses")
    void listsEveryWiredType() throws Exception {
        MvcResult result = mockMvc.perform(auth(get("/api/v1/imports/types"), orgA))
                .andExpect(status().isOk())
                .andReturn();

        List<String> slugs = objectMapper.readTree(result.getResponse().getContentAsString())
                .findValuesAsText("type");
        assertThat(slugs).containsExactlyInAnyOrder(
                "assets", "suppliers", "employees", "locations",
                "departments", "categories", "licenses", "contracts");
    }

    @Test
    @DisplayName("the field list carries everything the wizard renders a column row from")
    void fieldsCarryTheFullDescriptor() throws Exception {
        MvcResult result = mockMvc.perform(auth(get("/api/v1/imports/suppliers/fields"), orgA))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode fields = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode name = fields.get(0);
        assertThat(name.get("name").asText()).isEqualTo("name");
        assertThat(name.get("label").asText()).isEqualTo("Supplier name");
        assertThat(name.get("required").asBoolean()).isTrue();
        assertThat(name.get("dataType").asText()).isEqualTo("STRING");
        assertThat(name.get("example").asText()).isNotBlank();
        assertThat(name.get("aliases").isArray()).isTrue();

        // An enum field must publish its allowed values, or the wizard cannot show them.
        JsonNode status = fields.get(fields.size() - 1);
        assertThat(status.get("enumValues").findValuesAsText("").size()).isZero();
        assertThat(status.get("enumValues").toString()).contains("ACTIVE");
    }

    @Test
    @DisplayName("the template downloads with a filename in Content-Disposition")
    void templateDownloadsWithAFilename() throws Exception {
        mockMvc.perform(auth(get("/api/v1/imports/suppliers/template?format=xlsx"), orgA))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"assetiq-suppliers-import-template.xlsx\""))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        mockMvc.perform(auth(get("/api/v1/imports/licenses/template?format=csv"), orgA))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"assetiq-licenses-import-template.csv\""));
    }

    // ── Analyse, preview, commit ──────────────────────────────────────────────

    @Test
    @DisplayName("a foreign-platform header set is analysed, previewed and committed")
    void analysePreviewCommit() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String csv = String.join("\n",
                "Vendor Name,Contact Email,TIN,Telephone",
                "Acme " + suffix + ",sales@acme.example,C001,+233201234567",
                "Globex " + suffix + ",hello@globex.example,C002,+233201234568") + "\n";

        JsonNode analysis = analyse("suppliers", orgA, "vendors.csv", csv);
        UUID uploadId = UUID.fromString(analysis.get("uploadId").asText());

        assertThat(analysis.get("rowCount").asInt()).isEqualTo(2);
        // "Vendor Name" is an alias of the supplier name; "Contact Email" of email.
        assertThat(analysis.get("suggestedMapping").get("name").asInt()).isZero();
        assertThat(analysis.get("suggestedMapping").get("email").asInt()).isEqualTo(1);
        assertThat(analysis.get("missingRequiredFields")).isEmpty();
        assertThat(analysis.get("detectedColumns").get(0).get("sampleValues").get(0).asText())
                .startsWith("Acme ");

        // The user corrects the mapping by hand: TIN is the tax id, not registration.
        Map<String, Object> mapping = Map.of("name", 0, "email", 1, "taxId", 2, "phone", 3);

        JsonNode preview = postJson("/api/v1/imports/suppliers/preview", orgA,
                Map.of("uploadId", uploadId, "mapping", mapping,
                        "options", Map.of("skipInvalidRows", true)));
        assertThat(preview.get("totals").get("total").asInt()).isEqualTo(2);
        assertThat(preview.get("totals").get("valid").asInt()).isEqualTo(2);
        assertThat(preview.get("totals").get("invalid").asInt()).isZero();

        // Nothing was written by the preview.
        assertThat(listBody("/api/v1/suppliers?limit=200", orgA)).doesNotContain("Acme " + suffix);

        JsonNode job = postJson("/api/v1/imports/suppliers/commit", orgA,
                Map.of("uploadId", uploadId, "mapping", mapping,
                        "options", Map.of("skipInvalidRows", true)));
        UUID jobId = UUID.fromString(job.get("jobId").asText());
        assertThat(job.get("entityType").asText()).isEqualTo("suppliers");

        JsonNode finished = awaitJob(jobId, orgA);
        assertThat(finished.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(finished.get("result").get("imported").asInt()).isEqualTo(2);
        assertThat(finished.get("result").get("errors")).isEmpty();

        String suppliers = listBody("/api/v1/suppliers?limit=200", orgA);
        assertThat(suppliers).contains("Acme " + suffix).contains("Globex " + suffix)
                .contains("C001");
    }

    @Test
    @DisplayName("preview reports bad rows by row number and by the user's own column header")
    void previewCatchesBadRowsWithoutWriting() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String csv = String.join("\n",
                "Vendor Name,Contact Email",
                "Good " + suffix + ",fine@example.com",
                ",orphan@example.com",
                "Bad Email " + suffix + ",not-an-email") + "\n";

        JsonNode analysis = analyse("suppliers", orgA, "vendors.csv", csv);
        UUID uploadId = UUID.fromString(analysis.get("uploadId").asText());

        JsonNode preview = postJson("/api/v1/imports/suppliers/preview", orgA,
                Map.of("uploadId", uploadId, "mapping", Map.of("name", 0, "email", 1)));

        assertThat(preview.get("totals").get("total").asInt()).isEqualTo(3);
        assertThat(preview.get("totals").get("valid").asInt()).isEqualTo(1);
        assertThat(preview.get("totals").get("invalid").asInt()).isEqualTo(2);

        JsonNode errors = preview.get("errors");
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).get("row").asInt()).isEqualTo(3);
        assertThat(errors.get(0).get("column").asText()).isEqualTo("Vendor Name");
        assertThat(errors.get(1).get("row").asInt()).isEqualTo(4);
        assertThat(errors.get(1).get("column").asText()).isEqualTo("Contact Email");

        assertThat(listBody("/api/v1/suppliers?limit=200", orgA))
                .as("a preview must not write")
                .doesNotContain("Good " + suffix);
    }

    @Test
    @DisplayName("committing the same file twice skips the duplicates rather than doubling them")
    void duplicateSkipIsTheDefault() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String csv = "Vendor Name\nRepeat " + suffix + "\n";
        Map<String, Object> mapping = Map.of("name", 0);

        UUID firstUpload = UUID.fromString(analyse("suppliers", orgA, "v.csv", csv).get("uploadId").asText());
        JsonNode first = awaitJob(UUID.fromString(postJson("/api/v1/imports/suppliers/commit", orgA,
                Map.of("uploadId", firstUpload, "mapping", mapping)).get("jobId").asText()), orgA);
        assertThat(first.get("result").get("imported").asInt()).isEqualTo(1);

        UUID secondUpload = UUID.fromString(analyse("suppliers", orgA, "v.csv", csv).get("uploadId").asText());
        JsonNode second = awaitJob(UUID.fromString(postJson("/api/v1/imports/suppliers/commit", orgA,
                Map.of("uploadId", secondUpload, "mapping", mapping)).get("jobId").asText()), orgA);

        assertThat(second.get("result").get("imported").asInt()).isZero();
        assertThat(second.get("result").get("skipped").asInt()).isEqualTo(1);
    }

    // ── Tenant isolation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("org B cannot preview or commit org A's staged upload")
    void stagedUploadsAreTenantScoped() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String csv = "Vendor Name\nAlpha Secret " + suffix + "\n";
        UUID uploadId = UUID.fromString(analyse("suppliers", orgA, "v.csv", csv).get("uploadId").asText());

        Map<String, Object> body = Map.of("uploadId", uploadId, "mapping", Map.of("name", 0));

        MvcResult preview = mockMvc.perform(auth(post("/api/v1/imports/suppliers/preview"), orgB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
        assertThat(preview.getResponse().getStatus()).isGreaterThanOrEqualTo(400);
        assertThat(preview.getResponse().getContentAsString()).doesNotContain(suffix);

        MvcResult commit = mockMvc.perform(auth(post("/api/v1/imports/suppliers/commit"), orgB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
        assertThat(commit.getResponse().getStatus()).isGreaterThanOrEqualTo(400);

        // And nothing from org A's file reached org B.
        assertThat(listBody("/api/v1/suppliers?limit=200", orgB)).doesNotContain(suffix);
    }

    @Test
    @DisplayName("org B cannot read, overwrite or delete org A's mapping preset")
    void mappingPresetsAreTenantScoped() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        JsonNode saved = postJson("/api/v1/imports/suppliers/mappings", orgA, Map.of(
                "name", "Alpha preset " + suffix,
                "mapping", Map.of("name", "Vendor Name", "email", "Contact Email")));
        UUID presetId = UUID.fromString(saved.get("id").asText());

        // Org A sees it.
        assertThat(listBody("/api/v1/imports/suppliers/mappings", orgA)).contains(suffix);

        // Org B does not.
        assertThat(listBody("/api/v1/imports/suppliers/mappings", orgB)).doesNotContain(suffix);

        MvcResult delete = mockMvc.perform(
                        auth(delete("/api/v1/imports/suppliers/mappings/" + presetId), orgB))
                .andReturn();
        assertThat(delete.getResponse().getStatus()).isGreaterThanOrEqualTo(400);

        // Still there for its owner.
        assertThat(listBody("/api/v1/imports/suppliers/mappings", orgA)).contains(suffix);
    }

    @Test
    @DisplayName("saving a preset under an existing name replaces it rather than duplicating it")
    void presetsAreUniqueByName() throws Exception {
        String name = "Monthly export " + UUID.randomUUID().toString().substring(0, 6);
        postJson("/api/v1/imports/contracts/mappings", orgA,
                Map.of("name", name, "mapping", Map.of("title", "Agreement")));
        JsonNode second = postJson("/api/v1/imports/contracts/mappings", orgA,
                Map.of("name", name, "mapping", Map.of("title", "Contract Title")));

        assertThat(second.get("mapping").get("title").asText()).isEqualTo("Contract Title");

        MvcResult listed = mockMvc.perform(auth(get("/api/v1/imports/contracts/mappings"), orgA))
                .andExpect(status().isOk()).andReturn();
        long matching = objectMapper.readTree(listed.getResponse().getContentAsString())
                .findValuesAsText("name").stream().filter(name::equals).count();
        assertThat(matching).isEqualTo(1);
    }

    @Test
    @DisplayName("a preset naming a field the type does not have is refused")
    void presetsAreValidatedAgainstTheDescriptors() throws Exception {
        mockMvc.perform(auth(post("/api/v1/imports/suppliers/mappings"), orgA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Broken " + UUID.randomUUID(),
                                "mapping", Map.of("notAField", "Some Column")))))
                .andExpect(status().is4xxClientError());
    }

    // ── Uploads are files from strangers ──────────────────────────────────────

    @Test
    @DisplayName("a renamed binary is refused even though the extension says .csv")
    void uploadsAreSniffedNotTrusted() throws Exception {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
        mockMvc.perform(multipart("/api/v1/imports/suppliers/analyse")
                        .file(new MockMultipartFile("file", "vendors.csv", "text/csv", png))
                        .header("Authorization", "Bearer " + orgA.token())
                        .header("X-Forwarded-For", nextClient()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("a format that is neither .xlsx nor .csv is refused")
    void onlyTwoFormatsAreAccepted() throws Exception {
        mockMvc.perform(multipart("/api/v1/imports/suppliers/analyse")
                        .file(new MockMultipartFile("file", "vendors.json", "application/json",
                                "[]".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + orgA.token())
                        .header("X-Forwarded-For", nextClient()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("an unauthenticated caller gets nothing from the wizard")
    void unauthenticatedCallersAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/imports/types").header("X-Forwarded-For", nextClient()))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/v1/imports/suppliers/template").header("X-Forwarded-For", nextClient()))
                .andExpect(status().is4xxClientError());
    }

    // ── The existing asset import still works ─────────────────────────────────

    @Test
    @DisplayName("the historical positional asset import still posts and completes")
    void legacyAssetImportStillWorks() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        byte[] workbook = legacyAssetWorkbook("Legacy Laptop " + suffix);

        MvcResult accepted = mockMvc.perform(multipart("/api/v1/import-jobs/assets")
                        .file(new MockMultipartFile("file", "assets.xlsx",
                                ImportUploadPolicy.XLSX_CONTENT_TYPE, workbook))
                        .header("Authorization", "Bearer " + orgA.token())
                        .header("X-Forwarded-For", nextClient()))
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode job = objectMapper.readTree(accepted.getResponse().getContentAsString());
        assertThat(job.get("entityType").asText()).isEqualTo("assets");

        JsonNode finished = awaitJob(UUID.fromString(job.get("jobId").asText()), orgA);
        assertThat(finished.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(finished.get("result").get("imported").asInt()).isEqualTo(1);
        assertThat(listBody("/api/v1/assets?limit=200", orgA)).contains("Legacy Laptop " + suffix);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** The 23-column positional layout the asset import has always accepted. */
    private static byte[] legacyAssetWorkbook(String name) throws Exception {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet();
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            String[] headers = {"name", "assetTag", "serialNumber", "description", "assetType",
                    "manufacturer", "model", "purchaseDate", "purchaseCost", "currency",
                    "depreciationMethod", "usefulLifeMonths", "residualValue", "warrantyExpiryDate",
                    "status", "condition", "category", "location", "supplier", "department",
                    "assignedUserEmail", "invoiceId", "insurancePolicyId"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(name);
            row.createCell(4).setCellValue("HARDWARE");
            wb.write(out);
            return out.toByteArray();
        }
    }

    private JsonNode analyse(String type, Tenant tenant, String filename, String csv) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/imports/" + type + "/analyse")
                        .file(new MockMultipartFile("file", filename, "text/csv",
                                csv.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + tenant.token())
                        .header("X-Forwarded-For", nextClient()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode postJson(String path, Tenant tenant, Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(auth(post(path), tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** Polls the shared import-job endpoint until the job leaves QUEUED/PROCESSING. */
    private JsonNode awaitJob(UUID jobId, Tenant tenant) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            MvcResult result = mockMvc.perform(auth(get("/api/v1/import-jobs/" + jobId), tenant))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode job = objectMapper.readTree(result.getResponse().getContentAsString());
            String status = job.get("status").asText();
            if (!"QUEUED".equals(status) && !"PROCESSING".equals(status)) {
                return job;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Import job " + jobId + " never finished");
    }

    private String listBody(String path, Tenant tenant) throws Exception {
        return mockMvc.perform(auth(get(path), tenant))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private Tenant registerTenant(String label) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        TenantRegisterRequest req = new TenantRegisterRequest();
        req.setOrganisationName(label + " Org " + suffix);
        req.setOrganisationContactEmail("ops+" + suffix + "@example.com");
        req.setAdminFirstName("Imp");
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

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", nextClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", req.getAdminEmail(),
                                "password", "Password123",
                                "organisationId", resp.getOrganisationId()))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString()).path("token").asText();
        return new Tenant(resp.getOrganisationId(), token, suffix);
    }

    private String nextClient() {
        return "10.1.0." + clientCounter.getAndIncrement();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, Tenant tenant) {
        String client = nextClient();
        return builder.header("Authorization", "Bearer " + tenant.token())
                .header("X-Client-ID", client)
                .header("X-Forwarded-For", client);
    }
}
