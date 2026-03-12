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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class WebhookServiceImpl extends TenantAwareService implements WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookServiceImpl.class);

    private final WebhookRepository webhookRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final HttpClient httpClient;

    public WebhookServiceImpl(OrganisationRepository organisationRepository,
                              WebhookRepository webhookRepository,
                              WebhookDeliveryRepository deliveryRepository) {
        super(organisationRepository);
        this.webhookRepository = webhookRepository;
        this.deliveryRepository = deliveryRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
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
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"event\":\"").append(eventName).append("\",");
        sb.append("\"timestamp\":\"").append(Instant.now()).append("\",");
        sb.append("\"data\":{");
        data.forEach((k, v) -> sb.append("\"").append(k).append("\":\"").append(v).append("\","));
        if (!data.isEmpty()) sb.deleteCharAt(sb.length() - 1);
        sb.append("}}");
        return sb.toString();
    }

    private WebhookDelivery fireAndRecord(Webhook wh, String eventName, String payload) {
        long start = System.currentTimeMillis();
        int statusCode = 0;
        String responseBody = "";
        String deliveryStatus = "failed";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(wh.getUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-Webhook-Event", eventName)
                    .header("X-Webhook-Id", wh.getId().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            statusCode = response.statusCode();
            responseBody = response.body() != null ? response.body().substring(0, Math.min(response.body().length(), 500)) : "";
            deliveryStatus = (statusCode >= 200 && statusCode < 300) ? "success" : "failed";
        } catch (Exception e) {
            responseBody = "Error: " + e.getMessage();
            log.warn("[Webhook] Delivery failed for webhook {} event {}: {}", wh.getId(), eventName, e.getMessage());
        }

        long elapsed = System.currentTimeMillis() - start;

        // Persist delivery record
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setWebhook(wh);
        delivery.setEventName(eventName);
        delivery.setPayload(payload);
        delivery.setStatusCode(statusCode);
        delivery.setResponseBody(responseBody);
        delivery.setResponseTimeMs(elapsed);
        delivery.setStatus(deliveryStatus);
        delivery.setOrganisation(wh.getOrganisation());
        WebhookDelivery saved = deliveryRepository.save(delivery);

        // Update webhook counters
        wh.setDeliveryCount(wh.getDeliveryCount() + 1);
        wh.setLastTriggeredAt(Instant.now());
        if ("failed".equals(deliveryStatus)) {
            wh.setFailureCount(wh.getFailureCount() + 1);
        }
        webhookRepository.save(wh);

        return saved;
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
