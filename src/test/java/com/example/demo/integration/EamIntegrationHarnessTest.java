package com.example.demo.integration;

import com.example.demo.dto.*;
import com.example.demo.dto.TenantRegisterRequest;
import com.example.demo.dto.TenantRegisterResponse;
import com.example.demo.dto.WebhookDeliveryDto;
import com.example.demo.dto.WebhookDto;
import com.example.demo.models.Organisation;
import com.example.demo.jobs.AuditRetentionJob;
import com.example.demo.repositories.AssetRepository;
import com.example.demo.repositories.AuditEventRepository;
import com.example.demo.repositories.OrganisationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EamIntegrationHarnessTest {

    private static final String PAYSTACK_SECRET = "sk_test_dummy_key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrganisationRepository organisationRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private AuditRetentionJob auditRetentionJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID organisationId;
    private String token;

    private HttpServer webhookServer;
    private final AtomicReference<String> webhookRequestBody = new AtomicReference<>();
    private final AtomicReference<String> webhookEventHeader = new AtomicReference<>();
    private final AtomicReference<String> webhookSignatureHeader = new AtomicReference<>();
    private final AtomicInteger rateLimitBypassCounter = new AtomicInteger(1);

    @AfterEach
    void tearDown() {
        if (webhookServer != null) {
            webhookServer.stop(0);
            webhookServer = null;
        }
        webhookRequestBody.set(null);
        webhookEventHeader.set(null);
        webhookSignatureHeader.set(null);
    }

    @BeforeEach
    void setupTenant() throws Exception {
        if (this.token != null && this.organisationId != null) {
            // Avoid re-registering tenants for every test method (rate limits + extra load).
            return;
        }
        // Use unique org/email per test so we can run with shared context.
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgName = "EAM Test Org " + suffix;
        String adminEmail = "admin+" + suffix + "@example.com";

        TenantRegisterRequest req = new TenantRegisterRequest();
        req.setOrganisationName(orgName);
        req.setOrganisationContactEmail("ops+" + suffix + "@example.com");
        req.setAdminFirstName("Admin");
        req.setAdminLastName("User");
        req.setAdminEmail(adminEmail);
        req.setPassword("Password123");

        req.setCountry("GH");
        req.setTimezone("UTC");
        req.setIndustry("IT");

        MvcResult result = mockMvc.perform(
                        post("/api/v1/tenant/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req))
                )
                .andExpect(status().isCreated())
                .andReturn();

        TenantRegisterResponse resp = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                TenantRegisterResponse.class
        );
        this.organisationId = resp.getOrganisationId();
        this.token = resp.getToken();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder auth(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b) {
        // Rate limiter uses client identifier (remote addr / X-Forwarded-For). Provide a unique value
        // per request so test traffic doesn't exhaust the shared bucket.
        String client = "10.0.0." + rateLimitBypassCounter.getAndIncrement();
        return b.header("Authorization", "Bearer " + token)
                .header("X-Client-ID", client)
                .header("X-Forwarded-For", client);
    }

    @Test
    void webhookTestDelivery_recordsDeliveryAndValidJsonPayload() throws Exception {
        // Local webhook receiver
        webhookServer = HttpServer.create(new InetSocketAddress(0), 0);
        webhookServer.createContext("/webhook", this::handleWebhook);
        webhookServer.start();

        int port = webhookServer.getAddress().getPort();
        String url = "http://localhost:" + port + "/webhook";

        WebhookDto webhookReq = new WebhookDto();
        webhookReq.setName("Test Webhook " + UUID.randomUUID());
        webhookReq.setUrl(url);
        webhookReq.setEvents(List.of("test.webhook"));
        webhookReq.setActive(true);

        MvcResult created = mockMvc.perform(
                        auth(post("/api/v1/webhooks")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(webhookReq)))
                )
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createdNode = objectMapper.readTree(created.getResponse().getContentAsString());
        UUID webhookId = UUID.fromString(createdNode.get("id").asText());
        String webhookSecret = createdNode.get("secret").asText();

        MvcResult testDelivery = mockMvc.perform(
                        auth(post("/api/v1/webhooks/" + webhookId + "/test"))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andReturn();

        WebhookDeliveryDto delivery = objectMapper.readValue(
                testDelivery.getResponse().getContentAsString(),
                WebhookDeliveryDto.class
        );

        // Validate delivery + payload
        org.junit.jupiter.api.Assertions.assertEquals("test.webhook", delivery.getEventName());
        org.junit.jupiter.api.Assertions.assertEquals("success", delivery.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(delivery.getPayload());
        org.junit.jupiter.api.Assertions.assertFalse(delivery.getPayload().isBlank());

        // Payload should be valid JSON with at least event/timestamp/data
        JsonNode payloadJson = objectMapper.readTree(delivery.getPayload());
        org.junit.jupiter.api.Assertions.assertEquals("test.webhook", payloadJson.get("event").asText());
        org.junit.jupiter.api.Assertions.assertNotNull(payloadJson.get("timestamp"));

        // Ensure the server received the same body
        org.junit.jupiter.api.Assertions.assertEquals(delivery.getPayload(), webhookRequestBody.get());
        org.junit.jupiter.api.Assertions.assertEquals("test.webhook", webhookEventHeader.get());

        // Validate signature header
        String expectedSig = hmacSha256Hex(delivery.getPayload(), webhookSecret);
        org.junit.jupiter.api.Assertions.assertEquals(expectedSig, webhookSignatureHeader.get());
    }

    private void handleWebhook(HttpExchange exchange) throws IOException {
        try (exchange) {
            byte[] bytes = exchange.getRequestBody().readAllBytes();
            String body = new String(bytes, StandardCharsets.UTF_8);
            webhookRequestBody.set(body);

            List<String> eventHeaders = exchange.getRequestHeaders().get("X-Webhook-Event");
            webhookEventHeader.set(eventHeaders == null || eventHeaders.isEmpty() ? null : eventHeaders.get(0));

            List<String> sigHeaders = exchange.getRequestHeaders().get("X-Webhook-Signature");
            webhookSignatureHeader.set(sigHeaders == null || sigHeaders.isEmpty() ? null : sigHeaders.get(0));

            String response = "ok";
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void webhookDelivery_retriesOnReceiverError_recordsAttempts() throws Exception {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger(0);

        webhookServer = HttpServer.create(new InetSocketAddress(0), 0);
        webhookServer.createContext("/webhook", exchange -> {
            try (exchange) {
                int c = calls.incrementAndGet();
                String response;
                int status;
                if (c == 1) {
                    status = 500;
                    response = "fail";
                } else {
                    status = 200;
                    response = "ok";
                }
                exchange.sendResponseHeaders(status, response.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
            }
        });
        webhookServer.start();

        int port = webhookServer.getAddress().getPort();
        String url = "http://localhost:" + port + "/webhook";

        WebhookDto webhookReq = new WebhookDto();
        webhookReq.setName("Retry Webhook " + UUID.randomUUID());
        webhookReq.setUrl(url);
        webhookReq.setEvents(List.of("test.webhook"));
        webhookReq.setActive(true);

        MvcResult created = mockMvc.perform(
                        auth(post("/api/v1/webhooks")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(webhookReq)))
                )
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createdNode = objectMapper.readTree(created.getResponse().getContentAsString());
        UUID webhookId = UUID.fromString(createdNode.get("id").asText());

        MvcResult testDelivery = mockMvc.perform(
                        auth(post("/api/v1/webhooks/" + webhookId + "/test"))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andReturn();

        WebhookDeliveryDto delivery = objectMapper.readValue(
                testDelivery.getResponse().getContentAsString(),
                WebhookDeliveryDto.class
        );

        org.junit.jupiter.api.Assertions.assertEquals("test.webhook", delivery.getEventName());
        org.junit.jupiter.api.Assertions.assertEquals("success", delivery.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(2, delivery.getAttempts());
        org.junit.jupiter.api.Assertions.assertEquals(2, calls.get());
    }

    @Test
    void assetImport_excel_uploadsOneAsset() throws Exception {
        byte[] excelBytes = buildMinimalAssetExcel();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "assets.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        MvcResult result = mockMvc.perform(
                        auth(multipart("/api/v1/assets/import").file(file))
                                .contentType(MediaType.MULTIPART_FORM_DATA)
                )
                .andExpect(status().isOk())
                .andReturn();

        AssetImportResultDto resp = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AssetImportResultDto.class
        );

        org.junit.jupiter.api.Assertions.assertTrue(resp.getTotalRows() >= 1);
        org.junit.jupiter.api.Assertions.assertEquals(1, resp.getImported());
        org.junit.jupiter.api.Assertions.assertEquals(0, resp.getErrors().size());

        // Optional sanity: at least one persisted asset for tenant org
        org.junit.jupiter.api.Assertions.assertTrue(
                assetRepository.countByOrganisationAndDeletedAtIsNull(
                        organisationRepository.findByIdAndDeletedAtIsNull(organisationId).orElseThrow()
                ) >= 1
        );
    }

    @Test
    void bulkImport_dryRun_validatesWithoutPersistingAssets() throws Exception {
        long before = assetRepository.countByOrganisationAndDeletedAtIsNull(
                organisationRepository.findByIdAndDeletedAtIsNull(organisationId).orElseThrow()
        );

        byte[] excelBytes = buildMinimalAssetExcel();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "assets.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        MvcResult result = mockMvc.perform(
                        auth(multipart("/api/v1/bulk/assets/import")
                                        .file(file)
                                        .param("dryRun", "true"))
                                .contentType(MediaType.MULTIPART_FORM_DATA)
                )
                .andExpect(status().isOk())
                .andReturn();

        AssetImportResultDto resp = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AssetImportResultDto.class
        );

        org.junit.jupiter.api.Assertions.assertTrue(resp.isDryRun());
        org.junit.jupiter.api.Assertions.assertEquals(1, resp.getImported());
        org.junit.jupiter.api.Assertions.assertEquals(0, resp.getSkipped());
        org.junit.jupiter.api.Assertions.assertEquals(0, resp.getErrors().size());

        long after = assetRepository.countByOrganisationAndDeletedAtIsNull(
                organisationRepository.findByIdAndDeletedAtIsNull(organisationId).orElseThrow()
        );
        org.junit.jupiter.api.Assertions.assertEquals(before, after);
    }

    @Test
    void importJob_assets_asyncCompletesAndPersistsAssets() throws Exception {
        long beforeCount = assetRepository.countByOrganisationAndDeletedAtIsNull(
                organisationRepository.findByIdAndDeletedAtIsNull(organisationId).orElseThrow()
        );

        byte[] excelBytes = buildMinimalAssetExcel();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "assets.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        MvcResult created = mockMvc.perform(
                        auth(multipart("/api/v1/import-jobs/assets")
                                        .file(file)
                                        .param("dryRun", "false"))
                                .contentType(MediaType.MULTIPART_FORM_DATA)
                )
                .andExpect(status().isAccepted())
                .andReturn();

        AssetImportJobDto job = objectMapper.readValue(
                created.getResponse().getContentAsString(),
                AssetImportJobDto.class
        );

        UUID jobId = job.getJobId();
        String status = job.getStatus();

        // Poll for completion
        for (int i = 0; i < 120; i++) { // ~6s max
            if ("COMPLETED".equals(status) || "FAILED".equals(status)) break;
            Thread.sleep(50);

            MvcResult poll = mockMvc.perform(
                            auth(get("/api/v1/import-jobs/" + jobId))
                                    .accept(MediaType.APPLICATION_JSON)
                    )
                    .andExpect(status().isOk())
                    .andReturn();

            AssetImportJobDto updated = objectMapper.readValue(
                    poll.getResponse().getContentAsString(),
                    AssetImportJobDto.class
            );
            status = updated.getStatus();

            if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                job = updated;
                break;
            }
        }

        org.junit.jupiter.api.Assertions.assertEquals("COMPLETED", job.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(job.getResult());
        org.junit.jupiter.api.Assertions.assertEquals(1, job.getResult().getImported());

        long count = assetRepository.countByOrganisationAndDeletedAtIsNull(
                organisationRepository.findByIdAndDeletedAtIsNull(organisationId).orElseThrow()
        );
        org.junit.jupiter.api.Assertions.assertEquals(beforeCount + 1, count);
    }

    @Test
    void auditRetention_softDeletesOldAuditEvents() {
        // Tenants start on FREEMIUM (auditRetentionDays=7), so create an event older than 7 days.
        Organisation organisation = organisationRepository.findByIdAndDeletedAtIsNull(organisationId).orElseThrow();

        UUID oldAuditId = UUID.randomUUID();
        Instant createdAt = Instant.now().minus(Duration.ofDays(8));

        jdbcTemplate.update(
                "INSERT INTO audit_event (id, organisation_id, method, path, response_status, success, created_at, updated_at, deleted_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL)",
                oldAuditId,
                organisation.getId(),
                "GET",
                "/api/v1/assets",
                200,
                true,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );

        org.junit.jupiter.api.Assertions.assertTrue(
                auditEventRepository.findByIdAndOrganisationAndDeletedAtIsNull(oldAuditId, organisation).isPresent()
        );

        auditRetentionJob.run();

        org.junit.jupiter.api.Assertions.assertTrue(
                auditEventRepository.findByIdAndOrganisationAndDeletedAtIsNull(oldAuditId, organisation).isEmpty()
        );
    }

    private byte[] buildMinimalAssetExcel() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Assets");

            // Header row (row 0) — importer expects column order but not header text values.
            Row header = sheet.createRow(0);
            for (int c = 0; c <= 22; c++) {
                header.createCell(c).setCellValue("col" + c);
            }

            // Data row (row 1) — only `name` is required.
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("Asset " + Instant.now().toEpochMilli());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void reportGeneration_andDownload_returnsNonEmptyCsv() throws Exception {
        Map<String, Object> req = Map.of("format", "CSV");

        MvcResult created = mockMvc.perform(
                        auth(post("/api/v1/reports/assets")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                )
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode node = objectMapper.readTree(created.getResponse().getContentAsString());
        UUID reportId = UUID.fromString(node.get("reportId").asText());
        String downloadUrl = node.get("downloadUrl").asText();

        MvcResult dl = mockMvc.perform(
                        auth(get(downloadUrl))
                                .accept(MediaType.valueOf("text/csv"))
                )
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                .andReturn();

        byte[] csvBytes = dl.getResponse().getContentAsByteArray();
        org.junit.jupiter.api.Assertions.assertNotNull(csvBytes);
        org.junit.jupiter.api.Assertions.assertTrue(csvBytes.length > 0);

        // History should include the report
        MvcResult history = mockMvc.perform(
                        auth(get("/api/v1/reports/history"))
                                .param("limit", "10")
                                .param("offset", "0")
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode historyNode = objectMapper.readTree(history.getResponse().getContentAsString());
        boolean found = false;
        for (JsonNode r : historyNode.get("items")) {
            if (reportId.toString().equals(r.get("reportId").asText())) {
                found = true;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(found);

        // Delete should remove report + make download return 404
        mockMvc.perform(
                        auth(delete("/api/v1/reports/" + reportId))
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        auth(get(downloadUrl))
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void billingPlans_arePublic() throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/v1/billing/plans")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode plans = objectMapper.readTree(result.getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertTrue(plans.isArray());
        org.junit.jupiter.api.Assertions.assertFalse(plans.isEmpty());
    }

    @Test
    void paystackWebhook_signatureValidation() throws Exception {
        String payload = "{\"event\":\"charge.success\",\"data\":{\"reference\":\"BILL_TEST_REF\"}}";

        // Missing signature
        mockMvc.perform(
                        post("/api/v1/billing/webhooks/paystack")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                )
                .andExpect(status().isUnauthorized());

        // Invalid signature
        mockMvc.perform(
                        post("/api/v1/billing/webhooks/paystack")
                                .header("x-paystack-signature", "invalid")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                )
                .andExpect(status().isUnauthorized());

        // Valid signature
        String signature = hmacSha512Hex(payload, PAYSTACK_SECRET);
        mockMvc.perform(
                        post("/api/v1/billing/webhooks/paystack")
                                .header("x-paystack-signature", signature)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                )
                .andExpect(status().isOk());
    }

    private static String hmacSha512Hex(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        char[] digits = "0123456789abcdef".toCharArray();
        char[] hex = new char[digest.length * 2];
        for (int i = 0; i < digest.length; i++) {
            int v = digest[i] & 0xFF;
            hex[i * 2] = digits[v >>> 4];
            hex[i * 2 + 1] = digits[v & 0x0F];
        }
        return new String(hex);
    }

    private static String hmacSha256Hex(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        char[] digits = "0123456789abcdef".toCharArray();
        char[] hex = new char[digest.length * 2];
        for (int i = 0; i < digest.length; i++) {
            int v = digest[i] & 0xFF;
            hex[i * 2] = digits[v >>> 4];
            hex[i * 2 + 1] = digits[v & 0x0F];
        }
        return new String(hex);
    }
}
