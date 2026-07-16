package com.assetiq.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Guards the Paystack webhook endpoint against replay attacks.
 *
 * Strategy:
 *  1. Extract the event {@code id} from the JSON payload.
 *  2. On first receipt: store "seen" in Redis with a {@value #NONCE_TTL_HOURS}-hour TTL.
 *  3. On replay: the key already exists → reject.
 *
 * The TTL is intentionally generous (24 h) because Paystack can re-deliver
 * legitimate events for up to several hours after a transient 5xx from us.
 * Using the event {@code id} (not a timestamp) means we never drop a genuine
 * re-delivery of an event we failed to process.
 */
@Component
@Slf4j
public class WebhookReplayProtector {

    private static final String KEY_PREFIX      = "webhook:paystack:seen:";
    private static final int    NONCE_TTL_HOURS = 24;

    private final StringRedisTemplate redis;
    private final ObjectMapper        objectMapper;

    @Value("${app.webhook.replay-protection.enabled:true}")
    private boolean enabled;

    public WebhookReplayProtector(ObjectProvider<StringRedisTemplate> redisProvider, ObjectMapper objectMapper) {
        this.redis        = redisProvider.getIfAvailable();
        this.objectMapper = objectMapper;
    }

    /**
     * Returns {@code true} if this event has already been processed (replay detected).
     * Side effect: marks the event as seen in Redis when returning {@code false}.
     *
     * @param payload raw JSON body of the Paystack webhook
     */
    public boolean isReplay(String payload) {
        if (!enabled) return false;
        if (redis == null) {
            log.warn("[WEBHOOK-REPLAY] Redis unavailable — skipping replay protection");
            return false;
        }

        String eventId = extractEventId(payload);
        if (eventId == null || eventId.isBlank()) {
            // Paystack events always have an id; absence is suspicious — let the
            // signature check be the sole gate and process it (non-blocking).
            log.warn("[WEBHOOK-REPLAY] Could not extract event id from payload — skipping replay check");
            return false;
        }

        String key    = KEY_PREFIX + eventId;
        Boolean isNew = redis.opsForValue().setIfAbsent(key, "1", Duration.ofHours(NONCE_TTL_HOURS));

        if (Boolean.FALSE.equals(isNew)) {
            log.warn("[WEBHOOK-REPLAY] Duplicate Paystack event detected — eventId={}", eventId);
            return true;
        }

        log.debug("[WEBHOOK-REPLAY] New event accepted — eventId={}", eventId);
        return false;
    }

    private String extractEventId(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode idNode = root.path("id");
            return idNode.isMissingNode() ? null : idNode.asText(null);
        } catch (Exception e) {
            log.warn("[WEBHOOK-REPLAY] Failed to parse payload for event id extraction", e);
            return null;
        }
    }
}
