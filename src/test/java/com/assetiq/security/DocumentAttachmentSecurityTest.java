package com.assetiq.security;

import com.assetiq.dto.TenantRegisterRequest;
import com.assetiq.dto.TenantRegisterResponse;
import com.assetiq.models.FeatureFlag;
import com.assetiq.repositories.FeatureFlagRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end security properties of {@code /api/v1/documents}.
 *
 * <p>Attachments are the only endpoint that takes arbitrary bytes from a user and
 * later hands them back to a browser, so two things have to hold and keep holding:
 * a tenant can only ever reach its own files, and nothing an uploader supplies —
 * the bytes, the declared type or the filename — can turn into script running in
 * AssetIQ's origin.</p>
 *
 * <p>The feature flag is enabled in {@code @BeforeEach} rather than relied on:
 * the test profile runs with Flyway disabled, so the V58 row that turns it on in a
 * real deployment is not present here.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Document attachment security")
class DocumentAttachmentSecurityTest {

    private static final String FLAG_KEY = "commercial.document-attachments";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FeatureFlagRepository featureFlagRepository;

    private final AtomicInteger clientCounter = new AtomicInteger(1);

    private Tenant orgA;
    private Tenant orgB;

    private record Tenant(UUID organisationId, String token, String suffix) {}

    @BeforeEach
    void setUp() throws Exception {
        enableAttachments();
        if (orgA == null || orgB == null) {
            orgA = registerTenant("Alpha");
            orgB = registerTenant("Beta");
        }
    }

    // ── Cross-tenant access ───────────────────────────────────────────────────

    @Nested
    @DisplayName("cross-tenant access")
    class CrossTenant {

        @Test
        @DisplayName("org B cannot list, read the URL of, download or delete org A's attachment")
        void orgBCannotReachOrgAsAttachment() throws Exception {
            UUID contractId = createContract(orgA);
            String marker = "AlphaSecret" + orgA.suffix();
            UUID attachmentId = upload(orgA, contractId, marker + ".pdf", "application/pdf", pdf(marker));

            // Positive control: without this the denials below could pass simply
            // because the attachment was never created.
            MvcResult ownerList = mockMvc.perform(
                            auth(get("/api/v1/documents"), orgA)
                                    .param("entityType", "CONTRACT")
                                    .param("entityId", contractId.toString()))
                    .andExpect(status().isOk())
                    .andReturn();
            assertThat(ownerList.getResponse().getContentAsString()).contains(marker);

            // LIST — org B asking about org A's entity id must get nothing back.
            MvcResult foreignList = mockMvc.perform(
                            auth(get("/api/v1/documents"), orgB)
                                    .param("entityType", "CONTRACT")
                                    .param("entityId", contractId.toString()))
                    .andReturn();
            assertThat(foreignList.getResponse().getContentAsString())
                    .as("org A's attachment must not appear in org B's listing")
                    .doesNotContain(marker);

            // URL
            assertDenied("/api/v1/documents/" + attachmentId + "/url", marker);

            // DOWNLOAD — the one that would hand over the actual bytes.
            assertDenied("/api/v1/documents/" + attachmentId + "/download", marker);

            // DELETE — a denial that still deleted would be worse than a read leak.
            mockMvc.perform(auth(delete("/api/v1/documents/" + attachmentId), orgB))
                    .andExpect(status().is4xxClientError());

            // Still downloadable by its owner: proves the delete did not land.
            mockMvc.perform(auth(get("/api/v1/documents/" + attachmentId + "/download"), orgA))
                    .andExpect(status().isOk());
        }

        private void assertDenied(String path, String marker) throws Exception {
            MvcResult result = mockMvc.perform(auth(get(path), orgB)).andReturn();
            assertThat(result.getResponse().getStatus())
                    .as("GET %s from a foreign tenant must not succeed", path)
                    .isGreaterThanOrEqualTo(400);
            assertThat(result.getResponse().getContentAsString())
                    .as("GET %s must not leak the owning tenant's data", path)
                    .doesNotContain(marker);
        }
    }

    // ── Upload rules ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("upload rules")
    class UploadRules {

        @Test
        @DisplayName("rejects an SVG, which would execute script in AssetIQ's origin")
        void rejectsSvg() throws Exception {
            UUID contractId = createContract(orgA);
            byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                    .getBytes(StandardCharsets.UTF_8);

            mockMvc.perform(uploadRequest(orgA, contractId,
                            new MockMultipartFile("file", "logo.svg", "image/svg+xml", svg)))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("rejects HTML, whatever content type it claims")
        void rejectsHtmlUnderAnyLabel() throws Exception {
            UUID contractId = createContract(orgA);
            byte[] html = "<!DOCTYPE html><html><script>alert(1)</script></html>"
                    .getBytes(StandardCharsets.UTF_8);

            mockMvc.perform(uploadRequest(orgA, contractId,
                            new MockMultipartFile("file", "x.html", "text/html", html)))
                    .andExpect(status().is4xxClientError());

            // The interesting case: the same payload wearing a permitted label.
            mockMvc.perform(uploadRequest(orgA, contractId,
                            new MockMultipartFile("file", "invoice.pdf", "application/pdf", html)))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("rejects a file whose bytes contradict its declared content type")
        void rejectsContentTypeMismatch() throws Exception {
            UUID contractId = createContract(orgA);

            mockMvc.perform(uploadRequest(orgA, contractId,
                            new MockMultipartFile("file", "photo.png", "image/png", pdf("x"))))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("rejects an executable, which is not on the allow-list")
        void rejectsExecutable() throws Exception {
            UUID contractId = createContract(orgA);

            mockMvc.perform(uploadRequest(orgA, contractId,
                            new MockMultipartFile("file", "setup.exe", "application/x-msdownload",
                                                  new byte[]{'M', 'Z', 0, 0})))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("rejects a file over the size limit")
        void rejectsOversizedFile() throws Exception {
            UUID contractId = createContract(orgA);
            byte[] big = new byte[(int) (26L * 1024 * 1024)];
            System.arraycopy("%PDF".getBytes(StandardCharsets.US_ASCII), 0, big, 0, 4);

            MvcResult result = mockMvc.perform(uploadRequest(orgA, contractId,
                            new MockMultipartFile("file", "big.pdf", "application/pdf", big)))
                    .andReturn();

            assertThat(result.getResponse().getStatus())
                    .as("a 26 MB upload must be refused, not stored")
                    .isGreaterThanOrEqualTo(400);
        }

        @Test
        @DisplayName("a hostile filename never becomes part of the storage path")
        void sanitisesHostileFilename() throws Exception {
            UUID contractId = createContract(orgA);
            UUID id = upload(orgA, contractId, "../../../etc/passwd.pdf",
                             "application/pdf", pdf("traversal"));

            MvcResult listed = mockMvc.perform(
                            auth(get("/api/v1/documents"), orgA)
                                    .param("entityType", "CONTRACT")
                                    .param("entityId", contractId.toString()))
                    .andExpect(status().isOk())
                    .andReturn();

            String body = listed.getResponse().getContentAsString();
            assertThat(body).contains("passwd.pdf");
            assertThat(body).doesNotContain("..");
            // And it is still a working attachment, not a rejected one.
            mockMvc.perform(auth(get("/api/v1/documents/" + id + "/download"), orgA))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("SOFTWARE_LICENSE is an accepted entity type, so licence documents can be real files")
        void acceptsSoftwareLicenceAttachments() throws Exception {
            UUID licenceId = UUID.fromString(objectMapper.readTree(
                    mockMvc.perform(auth(post("/api/v1/licenses"), orgA)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(Map.of(
                                            "name", "Alpha Licence " + orgA.suffix(),
                                            "vendor", "Alpha Vendor",
                                            "licenseType", "SUBSCRIPTION",
                                            "status", "ACTIVE"))))
                            .andExpect(status().is2xxSuccessful())
                            .andReturn()
                            .getResponse().getContentAsString())
                    .get("id").asText());

            mockMvc.perform(multipartUpload(orgA, "SOFTWARE_LICENSE", licenceId,
                            new MockMultipartFile("file", "licence.pdf", "application/pdf",
                                                  pdf("licence"))))
                    .andExpect(status().isCreated());
        }
    }

    // ── Download hardening ────────────────────────────────────────────────────

    @Test
    @DisplayName("downloads are served as non-sniffable attachments, never as a page")
    void downloadIsNotRenderable() throws Exception {
        UUID contractId = createContract(orgA);
        UUID id = upload(orgA, contractId, "notes.txt", "text/plain",
                         "plain notes".getBytes(StandardCharsets.UTF_8));

        MvcResult result = mockMvc.perform(auth(get("/api/v1/documents/" + id + "/download"), orgA))
                .andExpect(status().isOk())
                .andReturn();

        // Together these are what stop an uploaded file executing in the app's
        // origin even if something did slip past the upload allow-list.
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .as("must be a download, not an inline render")
                .startsWith("attachment;");
        assertThat(result.getResponse().getHeader("X-Content-Type-Options"))
                .isEqualTo("nosniff");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void enableAttachments() {
        FeatureFlag flag = featureFlagRepository.findByKey(FLAG_KEY).orElseGet(FeatureFlag::new);
        flag.setKey(FLAG_KEY);
        flag.setDescription("Document attachments (enabled for this test).");
        flag.setEnabledGlobally(true);
        flag.setRolloutPercentage((short) 100);
        featureFlagRepository.save(flag);
    }

    private static byte[] pdf(String marker) {
        return ("%PDF-1.7\n% " + marker + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private UUID createContract(Tenant tenant) throws Exception {
        String s = UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mockMvc.perform(auth(post("/api/v1/contracts"), tenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Contract " + s,
                                "contractNumber", "CTR-" + s,
                                "contractType", "SERVICE_LEVEL_AGREEMENT",
                                "status", "DRAFT",
                                "startDate", "2026-01-01",
                                "endDate", "2026-12-31",
                                "alertDaysBefore", 30))))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(
                result.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID upload(Tenant tenant, UUID entityId, String filename,
                        String contentType, byte[] bytes) throws Exception {
        MvcResult result = mockMvc.perform(uploadRequest(tenant, entityId,
                        new MockMultipartFile("file", filename, contentType, bytes)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(
                result.getResponse().getContentAsString()).get("id").asText());
    }

    private MockHttpServletRequestBuilder uploadRequest(Tenant tenant, UUID entityId,
                                                        MockMultipartFile file) {
        return multipartUpload(tenant, "CONTRACT", entityId, file);
    }

    private MockHttpServletRequestBuilder multipartUpload(Tenant tenant, String entityType,
                                                          UUID entityId, MockMultipartFile file) {
        return auth(multipart("/api/v1/documents").file(file), tenant)
                .param("entityType", entityType)
                .param("entityId", entityId.toString());
    }

    private Tenant registerTenant(String label) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        TenantRegisterRequest req = new TenantRegisterRequest();
        req.setOrganisationName(label + " Docs Org " + suffix);
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

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", nextClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", req.getAdminEmail(),
                                "password", "Password123",
                                "organisationId", resp.getOrganisationId()))))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(login.getResponse().getContentAsString())
                .path("token").asText();
        return new Tenant(resp.getOrganisationId(), token, suffix);
    }

    private String nextClient() {
        return "10.1.0." + clientCounter.getAndIncrement();
    }

    private <B extends MockHttpServletRequestBuilder> B auth(B builder, Tenant tenant) {
        String client = nextClient();
        builder.header("Authorization", "Bearer " + tenant.token())
                .header("X-Client-ID", client)
                .header("X-Forwarded-For", client);
        return builder;
    }
}
