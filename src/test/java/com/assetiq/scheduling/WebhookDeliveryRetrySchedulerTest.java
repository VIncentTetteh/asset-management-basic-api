package com.assetiq.scheduling;

import com.assetiq.models.Webhook;
import com.assetiq.models.WebhookDelivery;
import com.assetiq.repositories.WebhookDeliveryRepository;
import com.assetiq.repositories.WebhookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookDeliveryRetryScheduler")
class WebhookDeliveryRetrySchedulerTest {

    @Mock WebhookDeliveryRepository deliveryRepository;
    @Mock WebhookRepository         webhookRepository;

    WebhookDeliveryRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new WebhookDeliveryRetryScheduler(deliveryRepository, webhookRepository);
        ReflectionTestUtils.setField(scheduler, "maxTotalAttempts",   20);
        ReflectionTestUtils.setField(scheduler, "maxAgeHours",        24);
        ReflectionTestUtils.setField(scheduler, "minRetryGapMinutes", 5);
        ReflectionTestUtils.setField(scheduler, "httpTimeoutSeconds", 5);
    }

    // ── No eligible deliveries ────────────────────────────────────────────────

    @Test
    @DisplayName("does nothing when no retryable deliveries exist")
    void noRetryable_doesNothing() {
        when(deliveryRepository.findRetryableDeliveries(any(), anyInt(), any()))
                .thenReturn(Collections.emptyList());

        scheduler.retryFailedDeliveries();

        verify(deliveryRepository, never()).save(any());
        verifyNoInteractions(webhookRepository);
    }

    // ── Abandoned webhook ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("abandoned deliveries")
    class Abandoned {

        @Test
        @DisplayName("marks delivery as 'abandoned' when webhook is inactive")
        void inactiveWebhook_abandoned() {
            Webhook wh = webhook("https://example.com", false);
            WebhookDelivery delivery = delivery(wh, 2, "failed");
            when(deliveryRepository.findRetryableDeliveries(any(), anyInt(), any()))
                    .thenReturn(List.of(delivery));

            scheduler.retryFailedDeliveries();

            ArgumentCaptor<WebhookDelivery> captor = ArgumentCaptor.forClass(WebhookDelivery.class);
            verify(deliveryRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("abandoned");
            verifyNoInteractions(webhookRepository);
        }

        @Test
        @DisplayName("marks delivery as 'abandoned' when webhook is soft-deleted")
        void deletedWebhook_abandoned() {
            Webhook wh = webhook("https://example.com", true);
            wh.setDeletedAt(Instant.now().minusSeconds(60));
            WebhookDelivery delivery = delivery(wh, 1, "failed");
            when(deliveryRepository.findRetryableDeliveries(any(), anyInt(), any()))
                    .thenReturn(List.of(delivery));

            scheduler.retryFailedDeliveries();

            ArgumentCaptor<WebhookDelivery> captor = ArgumentCaptor.forClass(WebhookDelivery.class);
            verify(deliveryRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("abandoned");
        }

        @Test
        @DisplayName("marks delivery as 'abandoned' when webhook is null")
        void nullWebhook_abandoned() {
            WebhookDelivery delivery = delivery(null, 1, "failed");
            when(deliveryRepository.findRetryableDeliveries(any(), anyInt(), any()))
                    .thenReturn(List.of(delivery));

            scheduler.retryFailedDeliveries(); // must not throw

            ArgumentCaptor<WebhookDelivery> captor = ArgumentCaptor.forClass(WebhookDelivery.class);
            verify(deliveryRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("abandoned");
        }
    }

    // ── Signature computation ─────────────────────────────────────────────────

    @Test
    @DisplayName("computeSignature returns consistent HMAC-SHA256 hex string")
    void computeSignature_consistentHmac() throws Exception {
        // Access private method via reflection for isolated testing
        java.lang.reflect.Method m = WebhookDeliveryRetryScheduler.class
                .getDeclaredMethod("computeSignature", String.class, String.class);
        m.setAccessible(true);

        String sig1 = (String) m.invoke(scheduler, "{\"event\":\"test\"}", "secret123");
        String sig2 = (String) m.invoke(scheduler, "{\"event\":\"test\"}", "secret123");

        assertThat(sig1).isNotNull().isNotBlank();
        assertThat(sig1).isEqualTo(sig2);          // deterministic
        assertThat(sig1).matches("[0-9a-f]{64}");  // hex-encoded SHA-256
    }

    @Test
    @DisplayName("computeSignature returns null when secret is blank")
    void computeSignature_nullWhenNoSecret() throws Exception {
        java.lang.reflect.Method m = WebhookDeliveryRetryScheduler.class
                .getDeclaredMethod("computeSignature", String.class, String.class);
        m.setAccessible(true);

        Object result = m.invoke(scheduler, "{}", "");
        assertThat(result).isNull();
    }

    // ── Repository query parameters ───────────────────────────────────────────

    @Test
    @DisplayName("queries repository with correct gap, max-attempts, and age cutoff")
    void queriesRepositoryWithCorrectParams() {
        when(deliveryRepository.findRetryableDeliveries(any(), anyInt(), any()))
                .thenReturn(Collections.emptyList());

        Instant before = Instant.now();
        scheduler.retryFailedDeliveries();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> gapCaptor   = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Integer> maxCaptor   = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Instant> ageCaptor   = ArgumentCaptor.forClass(Instant.class);

        verify(deliveryRepository).findRetryableDeliveries(
                gapCaptor.capture(), maxCaptor.capture(), ageCaptor.capture());

        // lastAttemptBefore ≈ now - 5 min
        assertThat(gapCaptor.getValue())
                .isBefore(before.minusSeconds(5 * 60 - 2))
                .isAfter(after.minusSeconds(5 * 60 + 2));

        // maxTotalAttempts
        assertThat(maxCaptor.getValue()).isEqualTo(20);

        // createdAfter ≈ now - 24h
        assertThat(ageCaptor.getValue())
                .isBefore(before.minusSeconds(24 * 3600 - 2))
                .isAfter(after.minusSeconds(24 * 3600 + 2));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Webhook webhook(String url, boolean active) {
        Webhook wh = new Webhook();
        wh.setId(UUID.randomUUID());
        wh.setUrl(url);
        wh.setActive(active);
        wh.setSecret("test-secret");
        return wh;
    }

    private WebhookDelivery delivery(Webhook wh, int attempts, String status) {
        WebhookDelivery d = new WebhookDelivery();
        d.setId(UUID.randomUUID());
        d.setWebhook(wh);
        d.setAttempts(attempts);
        d.setStatus(status);
        d.setEventName("asset.created");
        d.setPayload("{\"id\":\"abc\"}");
        return d;
    }
}
