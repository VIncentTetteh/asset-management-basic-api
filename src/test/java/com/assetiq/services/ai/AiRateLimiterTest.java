package com.assetiq.services.ai;

import com.assetiq.config.NoOpRateLimiter;
import com.assetiq.config.RateLimiter;
import com.assetiq.config.RedisRateLimiter.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AiRateLimiter - a free-tier key is a shared, exhaustible resource")
class AiRateLimiterTest {

    private MutableClock  clock;
    private AiRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        // The shared limiter fails open when Redis is absent — exactly the
        // deployment this test stands in for.
        limiter = new AiRateLimiter(new NoOpRateLimiter(), clock);
        limiter.configure(5, 3, 2);
    }

    @Test
    @DisplayName("the per-minute burst stops a user hammering the endpoint")
    void perUserMinuteBurst() {
        UUID orgId = UUID.randomUUID();
        limiter.checkAndConsume(orgId, "a@example.com");
        limiter.checkAndConsume(orgId, "a@example.com");

        assertThatThrownBy(() -> limiter.checkAndConsume(orgId, "a@example.com"))
                .isInstanceOf(AiQuotaExceededException.class)
                .hasMessageContaining("faster than the assistant can answer");
    }

    @Test
    @DisplayName("the window reopens once it has passed")
    void windowReopens() {
        UUID orgId = UUID.randomUUID();
        limiter.checkAndConsume(orgId, "a@example.com");
        limiter.checkAndConsume(orgId, "a@example.com");
        clock.advance(Duration.ofMinutes(1));

        assertThatCode(() -> limiter.checkAndConsume(orgId, "a@example.com")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("one user's hourly allowance runs out before the organisation's")
    void perUserHourlyAllowance() {
        UUID orgId = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            limiter.checkAndConsume(orgId, "a@example.com");
            clock.advance(Duration.ofMinutes(1));
        }
        assertThatThrownBy(() -> limiter.checkAndConsume(orgId, "a@example.com"))
                .isInstanceOf(AiQuotaExceededException.class)
                .hasMessageContaining("hourly allowance");
    }

    @Test
    @DisplayName("several users together exhaust the organisation's hourly allowance")
    void perOrganisationHourlyAllowance() {
        UUID orgId = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            limiter.checkAndConsume(orgId, "user" + i + "@example.com");
        }
        assertThatThrownBy(() -> limiter.checkAndConsume(orgId, "fresh@example.com"))
                .isInstanceOf(AiQuotaExceededException.class)
                .hasMessageContaining("organisation has used its hourly allowance");
    }

    @Test
    @DisplayName("one organisation's spending does not limit another")
    void organisationsHaveSeparateBuckets() {
        UUID noisy = UUID.randomUUID();
        UUID quiet = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            limiter.checkAndConsume(noisy, "user" + i + "@example.com");
        }
        assertThatCode(() -> limiter.checkAndConsume(quiet, "someone@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a shared limiter that refuses is honoured even when the local window allows")
    void sharedLimiterIsHonoured() {
        RateLimiter alwaysRefuses = (tier, key, limit, window) -> new RateLimitResult(false, 0L, window);
        AiRateLimiter strict = new AiRateLimiter(alwaysRefuses, clock);
        strict.configure(5, 3, 2);

        assertThatThrownBy(() -> strict.checkAndConsume(UUID.randomUUID(), "a@example.com"))
                .isInstanceOf(AiQuotaExceededException.class);
    }

    @Test
    @DisplayName("the exception carries a Retry-After the client can obey")
    void carriesRetryAfter() {
        UUID orgId = UUID.randomUUID();
        limiter.checkAndConsume(orgId, "a@example.com");
        limiter.checkAndConsume(orgId, "a@example.com");

        assertThatThrownBy(() -> limiter.checkAndConsume(orgId, "a@example.com"))
                .isInstanceOfSatisfying(AiQuotaExceededException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(60));
    }

    /** A clock the test moves on purpose, so window boundaries are deterministic. */
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration by) {
            now = now.plus(by);
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
