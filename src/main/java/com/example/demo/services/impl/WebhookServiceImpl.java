package com.example.demo.services.impl;

import com.example.demo.dto.WebhookDeliveryDto;
import com.example.demo.dto.WebhookDto;
import com.example.demo.models.Organisation;
import com.example.demo.models.Webhook;
import com.example.demo.models.WebhookDelivery;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.repositories.WebhookDeliveryRepository;
import com.example.demo.repositories.WebhookRepository;
import com.example.demo.services.TenantAwareService;
import com.example.demo.services.WebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
@Transactional
public class WebhookServiceImpl extends TenantAwareService implements WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookServiceImpl.class);

    private final WebhookRepository webhookRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("${app.webhook.delivery.max-attempts:3}")
    private int maxDeliveryAttempts;

    @org.springframework.beans.factory.annotation.Value("${app.webhook.delivery.backoff-millis:250}")
    private long baseBackoffMillis;

    public WebhookServiceImpl(OrganisationRepository organisationRepository,
                              WebhookRepository webhookRepository,
                              WebhookDeliveryRepository deliveryRepository,
                              ObjectMapper objectMapper) {
        super(organisationRepository);
        this.webhookRepository = webhookRepository;
        this.deliveryRepository = deliveryRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public WebhookDto create(WebhookDto dto) {
        Organisation org = requireTenantOrg();
        Webhook wh = new Webhook();
        wh.setName(dto.getName());
        wh.setUrl(dto.getUrl());
        wh.setEvents(dto.getEvents() != null ? String.join(",", dto.getEvents()) : "");
        wh.setActive(dto.isActive());
        wh.setSecret(dto.getSecret() != null ? dto.getSecret() : UUID.randomUUID().toString().replace("-", ""));
        wh.setOrganisation(org);
        Webhook saved = webhookRepository.save(wh);
        WebhookDto result = toDto(saved);
        // Return secret only on creation
        result.setSecret(saved.getSecret());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WebhookDto> list() {
        Organisation org = requireTenantOrg();
        return webhookRepository.findByOrganisationAndDeletedAtIsNull(org)
                .stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WebhookDto getById(UUID id) {
        Organisation org = requireTenantOrg();
        Webhook wh = requireWebhook(id, org);
        return toDto(wh);
    }

    @Override
    public WebhookDto update(UUID id, WebhookDto dto) {
        Organisation org = requireTenantOrg();
        Webhook wh = requireWebhook(id, org);
        if (dto.getName() != null) wh.setName(dto.getName());
        if (dto.getUrl() != null) wh.setUrl(dto.getUrl());
        if (dto.getEvents() != null) wh.setEvents(String.join(",", dto.getEvents()));
        wh.setActive(dto.isActive());
        return toDto(webhookRepository.save(wh));
    }

    @Override
    public void delete(UUID id) {
        Organisation org = requireTenantOrg();
        Webhook wh = requireWebhook(id, org);
        wh.setDeletedAt(Instant.now());
        webhookRepository.save(wh);
    }

    @Override
    @Async
    public void dispatch(String eventName, Map<String, Object> data) {
        // Dispatched async — no TenantContext here; operate globally
        // For each org's active webhooks that subscribe to this event
        webhookRepository.findAll().stream()
                .filter(wh -> wh.getDeletedAt() == null
                        && wh.isActive()
                        && subscribesTo(wh, eventName))
                .forEach(wh -> fireAndRecord(wh, eventName, buildPayload(eventName, data)));
    }

    @Override
    public WebhookDeliveryDto test(UUID webhookId) {
        Organisation org = requireTenantOrg();
        Webhook wh = requireWebhook(webhookId, org);

        String payload = buildPayload("test.webhook", Map.of("test", true, "webhookId", webhookId.toString()));
        WebhookDelivery delivery = fireAndRecord(wh, "test.webhook", payload);
        return toDeliveryDto(delivery);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WebhookDeliveryDto> listDeliveries(UUID webhookId, String status, Pageable pageable) {
        Organisation org = requireTenantOrg();
        Webhook wh = requireWebhook(webhookId, org);
        Page<WebhookDelivery> page = (status != null && !status.isBlank())
                ? deliveryRepository.findByWebhookAndOrganisationAndStatusOrderByCreatedAtDesc(wh, org, status, pageable)
                : deliveryRepository.findByWebhookAndOrganisationOrderByCreatedAtDesc(wh, org, pageable);
        return page.map(this::toDeliveryDto);
    }

    @Override
    @Transactional(readOnly = true)
    public WebhookDeliveryDto getDelivery(UUID webhookId, UUID deliveryId) {
        Organisation org = requireTenantOrg();
        requireWebhook(webhookId, org);
        WebhookDelivery d = deliveryRepository.findByIdAndOrganisation(deliveryId, org)
                .orElseThrow(() -> new IllegalArgumentException("Delivery not found: " + deliveryId));
        return toDeliveryDto(d);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private Webhook requireWebhook(UUID id, Organisation org) {
        return webhookRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Webhook not found: " + id));
    }

    private boolean subscribesTo(Webhook wh, String eventName) {
        if (wh.getEvents() == null || wh.getEvents().isBlank()) return false;
        return Arrays.asList(wh.getEvents().split(",")).contains(eventName);
    }

    private String buildPayload(String eventName, Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", eventName);
        payload.put("timestamp", Instant.now().toString());
        payload.put("data", data == null ? Map.of() : data);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            // Extremely unlikely; fall back to a minimal JSON-ish string so dispatch still works.
            Map<String, Object> fallback = new HashMap<>(payload);
            fallback.put("data", data == null ? Map.of() : data);
            return fallback.toString();
        }
    }

    private WebhookDelivery fireAndRecord(Webhook wh, String eventName, String payload) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setWebhook(wh);
        delivery.setEventName(eventName);
        delivery.setPayload(payload);
        delivery.setOrganisation(wh.getOrganisation());

        int attempts = 0;
        int statusCode = 0;
        String responseBody = "";
        String deliveryStatus = "failed";
        long lastElapsedMs = 0;

        for (int attempt = 1; attempt <= maxDeliveryAttempts; attempt++) {
            attempts = attempt;
            long start = System.currentTimeMillis();
            try {
                HttpRequest.Builder req = HttpRequest.newBuilder()
                        .uri(URI.create(wh.getUrl()))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .header("X-Webhook-Event", eventName)
                        .header("X-Webhook-Id", wh.getId().toString())
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));

                String signature = computeWebhookSignature(payload, wh.getSecret());
                if (signature != null && !signature.isBlank()) {
                    req.header("X-Webhook-Signature", signature);
                }

                HttpResponse<String> response = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());
                statusCode = response.statusCode();
                responseBody = response.body() != null
                        ? response.body().substring(0, Math.min(response.body().length(), 500))
                        : "";

                deliveryStatus = (statusCode >= 200 && statusCode < 300) ? "success" : "failed";
            } catch (Exception e) {
                statusCode = 0;
                responseBody = "Error: " + e.getMessage();
                deliveryStatus = "failed";
                log.warn("[Webhook] Delivery failed (attempt {}/{}): webhook {} event {}: {}",
                        attempt, maxDeliveryAttempts, wh.getId(), eventName, e.getMessage());
            }

            lastElapsedMs = System.currentTimeMillis() - start;

            delivery.setAttempts(attempts);
            delivery.setStatusCode(statusCode);
            delivery.setResponseBody(responseBody);
            delivery.setResponseTimeMs(lastElapsedMs);
            delivery.setStatus(deliveryStatus);

            // Persist after each attempt so partial progress is visible.
            delivery = deliveryRepository.save(delivery);

            if ("success".equals(deliveryStatus)) {
                break;
            }

            if (attempt < maxDeliveryAttempts) {
                long backoff = baseBackoffMillis * (1L << Math.max(0, attempt - 1));
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // Update webhook counters based on final status.
        wh.setDeliveryCount(wh.getDeliveryCount() + 1);
        wh.setLastTriggeredAt(Instant.now());
        if ("failed".equals(deliveryStatus)) {
            wh.setFailureCount(wh.getFailureCount() + 1);
        }
        webhookRepository.save(wh);

        return delivery;
    }

    private String computeWebhookSignature(String payload, String secret) {
        if (payload == null || payload.isBlank() || secret == null || secret.isBlank()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return toHexLower(digest);
        } catch (Exception e) {
            log.warn("[Webhook] Signature computation failed: {}", e.getMessage());
            return null;
        }
    }

    private static String toHexLower(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] hex = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hex[i * 2] = digits[v >>> 4];
            hex[i * 2 + 1] = digits[v & 0x0F];
        }
        return new String(hex);
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    private WebhookDto toDto(Webhook wh) {
        WebhookDto dto = new WebhookDto();
        dto.setId(wh.getId());
        dto.setName(wh.getName());
        dto.setUrl(wh.getUrl());
        dto.setEvents(wh.getEvents() != null && !wh.getEvents().isBlank()
                ? Arrays.asList(wh.getEvents().split(",")) : List.of());
        dto.setActive(wh.isActive());
        dto.setDeliveryCount(wh.getDeliveryCount());
        dto.setFailureCount(wh.getFailureCount());
        dto.setLastTriggeredAt(wh.getLastTriggeredAt());
        dto.setCreatedAt(wh.getCreatedAt());
        // secret omitted on list/get
        return dto;
    }

    private WebhookDeliveryDto toDeliveryDto(WebhookDelivery d) {
        WebhookDeliveryDto dto = new WebhookDeliveryDto();
        dto.setDeliveryId(d.getId());
        dto.setWebhookId(d.getWebhook().getId());
        dto.setEventName(d.getEventName());
        dto.setPayload(d.getPayload());
        dto.setStatusCode(d.getStatusCode());
        dto.setResponseBody(d.getResponseBody());
        dto.setResponseTimeMs(d.getResponseTimeMs());
        dto.setAttempts(d.getAttempts());
        dto.setStatus(d.getStatus());
        dto.setTriggeredAt(d.getCreatedAt());
        return dto;
    }
}
