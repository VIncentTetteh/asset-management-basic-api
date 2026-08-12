package com.assetiq.scheduling;

import com.assetiq.models.Webhook;
import com.assetiq.models.WebhookDelivery;
import com.assetiq.repositories.WebhookDeliveryRepository;
import com.assetiq.repositories.WebhookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Dead-letter-queue (DLQ) retry scheduler for webhook deliveries.
 *
 * <p>The primary delivery path in {@link com.assetiq.services.impl.WebhookServiceImpl}
 * attempts up to {@code app.webhook.delivery.max-attempts} times with exponential backoff
 * within a single {@code @Async} task.  If the endpoint is down for longer than those
 * attempts cover, the delivery stays in {@code status=failed}.
 *
 * <p>This scheduler picks up those permanently-failed deliveries and retries them every
 * {@code app.webhook.dlq.retry-interval-ms} (default 5 minutes), up to
 * {@code app.webhook.dlq.max-total-attempts} cumulative attempts (default 20).
 * Events older than {@code app.webhook.dlq.max-age-hours} (default 24 h) are abandoned.
 *
 * <p>Retry spacing is enforced via {@code updatedAt}: only deliveries not touched for at
 * least {@code app.webhook.dlq.min-retry-gap-minutes} (default 5 min) are eligible, which
 * prevents hammering a flapping endpoint on every scheduler tick.
 */
@Component
public class WebhookDeliveryRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryRetryScheduler.class);

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookRepository webhookRepository;
    private final HttpClient httpClient;

    /** Maximum total attempts (primary + DLQ) before giving up. */
    @Value("${app.webhook.dlq.max-total-attempts:20}")
    private int maxTotalAttempts;

    /** Events older than this are abandoned entirely. */
    @Value("${app.webhook.dlq.max-age-hours:24}")
    private int maxAgeHours;

    /** Minimum gap between retry attempts for the same delivery (minutes). */
    @Value("${app.webhook.dlq.min-retry-gap-minutes:5}")
    private int minRetryGapMinutes;

    /** HTTP timeout per attempt (seconds). */
    @Value("${app.webhook.delivery.http-timeout-seconds:10}")
    private int httpTimeoutSeconds;

    public WebhookDeliveryRetryScheduler(WebhookDeliveryRepository deliveryRepository,
                                         WebhookRepository webhookRepository) {
        this.deliveryRepository = deliveryRepository;
        this.webhookRepository = webhookRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Run every 5 minutes (fixed delay; not fixed rate, so runs never overlap).
     */
    @Scheduled(fixedDelayString = "${app.webhook.dlq.retry-interval-ms:300000}")
    @SchedulerLock(name = "webhookDeliveryRetry", lockAtMostFor = "PT10M", lockAtLeastFor = "PT4M")
    public void retryFailedDeliveries() {
        // NOTE: No @Transactional here — this method makes HTTP calls per delivery.
        // Wrapping the entire loop in one transaction would hold a DB connection open
        // for the duration of every HTTP round-trip.  Instead, each deliveryRepository.save()
        // and webhookRepository.save() call gets its own auto-transaction from Spring Data.
        Instant now = Instant.now();
        Instant lastAttemptBefore = now.minus(Duration.ofMinutes(minRetryGapMinutes));
        Instant createdAfter = now.minus(Duration.ofHours(maxAgeHours));

        List<WebhookDelivery> retryable = deliveryRepository.findRetryableDeliveries(
                lastAttemptBefore, maxTotalAttempts, createdAfter);

        if (retryable.isEmpty()) return;

        log.info("[WebhookDLQ] Found {} delivery/deliveries eligible for retry", retryable.size());

        int succeeded = 0;
        int failed = 0;

        for (WebhookDelivery delivery : retryable) {
            Webhook wh = delivery.getWebhook();
            if (wh == null || !wh.isActive() || wh.getDeletedAt() != null) {
                // Webhook disabled or deleted — abandon this delivery silently
                delivery.setStatus("abandoned");
                deliveryRepository.save(delivery);
                continue;
            }

            boolean success = attemptDelivery(delivery, wh);
            if (success) succeeded++;
            else failed++;
        }

        log.info("[WebhookDLQ] DLQ retry cycle complete — succeeded={}, failed={}", succeeded, failed);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private boolean attemptDelivery(WebhookDelivery delivery, Webhook wh) {
        long start = System.currentTimeMillis();
        int newAttempts = delivery.getAttempts() + 1;
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(wh.getUrl()))
                    .timeout(Duration.ofSeconds(httpTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("X-Webhook-Event", delivery.getEventName())
                    .header("X-Webhook-Id", wh.getId().toString())
                    .header("X-Webhook-Retry", "true")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            delivery.getPayload() != null ? delivery.getPayload() : "{}",
                            StandardCharsets.UTF_8));

            String sig = computeSignature(delivery.getPayload(), wh.getSecret());
            if (sig != null) reqBuilder.header("X-Webhook-Signature", sig);

            HttpResponse<String> response = httpClient.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            long elapsed = System.currentTimeMillis() - start;
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            String newStatus = ok ? "success" : "failed";

            delivery.setAttempts(newAttempts);
            delivery.setStatusCode(response.statusCode());
            delivery.setResponseBody(truncate(response.body(), 500));
            delivery.setResponseTimeMs(elapsed);
            delivery.setStatus(newStatus);
            deliveryRepository.save(delivery);

            if (ok) {
                // Update webhook success counters
                wh.setLastTriggeredAt(Instant.now());
                webhookRepository.save(wh);
                log.info("[WebhookDLQ] Delivery {} succeeded on attempt {} (HTTP {})",
                        delivery.getId(), newAttempts, response.statusCode());
            } else {
                log.warn("[WebhookDLQ] Delivery {} still failing on attempt {} (HTTP {})",
                        delivery.getId(), newAttempts, response.statusCode());
            }
            return ok;

        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - start;
            delivery.setAttempts(newAttempts);
            delivery.setStatusCode(0);
            delivery.setResponseBody("Error: " + truncate(ex.getMessage(), 400));
            delivery.setResponseTimeMs(elapsed);
            delivery.setStatus("failed");
            deliveryRepository.save(delivery);

            log.warn("[WebhookDLQ] Delivery {} exception on attempt {}: {}",
                    delivery.getId(), newAttempts, ex.getMessage());
            return false;
        }
    }

    private String computeSignature(String payload, String secret) {
        if (payload == null || secret == null || secret.isBlank()) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[digest.length * 2];
            char[] digits = "0123456789abcdef".toCharArray();
            for (int i = 0; i < digest.length; i++) {
                hex[i * 2] = digits[(digest[i] >> 4) & 0xF];
                hex[i * 2 + 1] = digits[digest[i] & 0xF];
            }
            return new String(hex);
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
