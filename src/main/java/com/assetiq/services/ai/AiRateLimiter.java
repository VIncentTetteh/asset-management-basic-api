package com.assetiq.services.ai;

import com.assetiq.config.RateLimiter;
import com.assetiq.config.RedisRateLimiter.RateLimitResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Two-layer quota for the assistant.
 *
 * <p>The model key is a shared, exhaustible resource: one tenant looping a script
 * can spend the whole free tier and leave every other tenant with a broken
 * feature. So there are two windows — per organisation and per user — and both
 * must allow the call.
 *
 * <p>Layer one is the shared {@link RateLimiter}, which is Redis-backed in any
 * deployment that has Redis and correct across instances. Layer two is an
 * in-process fixed window that runs regardless. The second layer exists because
 * the shared limiter deliberately fails open when Redis is absent or down, and
 * failing open on a spend-limited third-party key is the wrong default: a Redis
 * outage would turn into a bill or a burnt quota. Both layers must pass.
 */
@Component
public class AiRateLimiter {

    static final String TIER_ORG_HOUR    = "ai:org:hour";
    static final String TIER_USER_HOUR   = "ai:user:hour";
    static final String TIER_USER_MINUTE = "ai:user:minute";

    private static final int HOUR_SECONDS   = 3600;
    private static final int MINUTE_SECONDS = 60;

    /** Above this many tracked windows, expired ones are swept before the next write. */
    private static final int MAX_TRACKED_WINDOWS = 10_000;

    private final RateLimiter shared;
    private final Clock clock;

    /** Calls one organisation may make per hour across all of its users. */
    @Value("${ai.rate-limit.organisation-per-hour:120}")
    private int organisationPerHour;

    /** Calls one user may make per hour. */
    @Value("${ai.rate-limit.user-per-hour:30}")
    private int userPerHour;

    /** Burst ceiling for one user, per minute. */
    @Value("${ai.rate-limit.user-per-minute:6}")
    private int userPerMinute;

    private final Map<String, Window> local = new ConcurrentHashMap<>();

    @Autowired
    public AiRateLimiter(RateLimiter shared) {
        this(shared, Clock.systemUTC());
    }

    AiRateLimiter(RateLimiter shared, Clock clock) {
        this.shared = shared;
        this.clock  = clock;
    }

    /**
     * Consumes one call for this user in this organisation.
     *
     * @throws AiQuotaExceededException when either window is exhausted
     */
    public void checkAndConsume(UUID organisationId, String userKey) {
        String org  = String.valueOf(organisationId);
        String user = org + "|" + (userKey == null ? "anonymous" : userKey);

        consume(TIER_USER_MINUTE, user, userPerMinute, MINUTE_SECONDS,
                "You are sending messages faster than the assistant can answer. Try again in a minute.");
        consume(TIER_USER_HOUR, user, userPerHour, HOUR_SECONDS,
                "You have used your hourly allowance for the assistant. Try again later.");
        consume(TIER_ORG_HOUR, org, organisationPerHour, HOUR_SECONDS,
                "Your organisation has used its hourly allowance for the assistant. Try again later.");
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private void consume(String tier, String key, int limit, int windowSecs, String message) {
        if (limit <= 0) {
            return;
        }
        RateLimitResult sharedResult = shared.tryConsume(tier, key, limit, windowSecs);
        boolean localAllowed = consumeLocal(tier + ":" + key, limit, windowSecs);
        if (!sharedResult.allowed() || !localAllowed) {
            throw new AiQuotaExceededException(message, windowSecs);
        }
    }

    /**
     * Fixed window in process memory. The window start is snapped to a multiple
     * of the window size so the boundary is deterministic and testable rather
     * than dependent on when the first call happened to arrive.
     */
    private boolean consumeLocal(String key, int limit, int windowSecs) {
        long windowIndex = clock.instant().getEpochSecond() / windowSecs;
        // Bound the map: a tenant churning user keys must not grow it without
        // limit. Stale windows carry no meaning once their index has passed.
        if (local.size() > MAX_TRACKED_WINDOWS) {
            local.values().removeIf(w -> w.index < windowIndex);
        }
        Window window = local.compute(key, (k, existing) ->
                existing != null && existing.index == windowIndex ? existing : new Window(windowIndex));
        return window.count.incrementAndGet() <= limit;
    }

    /** Visible for tests: forget every window. */
    public void reset() {
        local.clear();
    }

    public int organisationPerHour() {
        return organisationPerHour;
    }

    public int userPerHour() {
        return userPerHour;
    }

    public int userPerMinute() {
        return userPerMinute;
    }

    /** Visible for tests: override the configured limits. */
    public void configure(int organisationPerHour, int userPerHour, int userPerMinute) {
        this.organisationPerHour = organisationPerHour;
        this.userPerHour         = userPerHour;
        this.userPerMinute       = userPerMinute;
    }

    private static final class Window {
        private final long index;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long index) {
            this.index = index;
        }
    }
}
