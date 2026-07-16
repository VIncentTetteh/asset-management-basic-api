package com.assetiq.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import static com.assetiq.config.RateLimitingConfig.*;

/**
 * Distributed rate limiter at the Spring MVC interceptor layer.
 *
 * <h3>Tiers</h3>
 * <ul>
 *   <li><b>Auth tier</b> ({@code /api/v1/auth/**}, {@code /api/v1/mfa/**}):
 *       5 req/min + 20 req/hour — prevents brute-force credential attacks.</li>
 *   <li><b>General tier</b> (all other {@code /api/**}):
 *       100 req/min per client.</li>
 * </ul>
 *
 * <h3>Client key derivation (priority order)</h3>
 * <ol>
 *   <li>{@code X-Client-ID} header — explicit key for SDK / mobile clients.</li>
 *   <li>SHA-256 prefix of the Bearer token — unique per authenticated session;
 *       not reversible to the raw token.</li>
 *   <li>Leftmost IP in {@code X-Forwarded-For} — <em>only when the request
 *       arrives from a trusted proxy CIDR</em> (see {@link RateLimitingConfig#getTrustedProxyCidrs()}).
 *       This prevents header spoofing by untrusted clients.</li>
 *   <li>{@code remoteAddr} — final fallback.</li>
 * </ol>
 *
 * <h3>Distributed state</h3>
 * Bucket counters are stored through {@link RateLimiter}. Production uses Redis
 * so horizontally scaled instances share limits; test/local contexts without
 * Redis use a no-op implementation.
 *
 * <h3>Response headers (RFC 6585)</h3>
 * {@code X-RateLimit-Limit}, {@code X-RateLimit-Remaining},
 * {@code X-RateLimit-Reset}, {@code Retry-After} (only on 429).
 */
@Component
public class RateLimitingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingInterceptor.class);

    private static final String[] AUTH_PREFIXES = {"/api/v1/auth", "/api/v1/mfa"};
    private static final int MINUTE_SECS        = 60;
    private static final int HOUR_SECS          = 3600;

    private final RateLimiter        rateLimiter;
    private final RateLimitingConfig config;

    public RateLimitingInterceptor(RateLimiter rateLimiter, RateLimitingConfig config) {
        this.rateLimiter = rateLimiter;
        this.config      = config;
    }

    @Override
    public boolean preHandle(
            @NotNull HttpServletRequest  request,
            @NotNull HttpServletResponse response,
            @NotNull Object              handler) throws IOException {

        if (!config.isEnabled()) {
            return true;
        }

        String  path      = request.getRequestURI();
        String  clientKey = extractClientKey(request);
        boolean isAuth    = isAuthPath(path);

        if (isAuth) {
            return handleAuthTier(clientKey, path, response);
        } else {
            return handleApiTier(clientKey, path, response);
        }
    }

    // ── Tier handlers ─────────────────────────────────────────────────────────

    /**
     * Auth tier: enforce BOTH the per-minute AND the per-hour limits.
     * The request is rejected if either limit is exceeded.
     */
    private boolean handleAuthTier(String clientKey, String path,
                                   HttpServletResponse response) throws IOException {

        RedisRateLimiter.RateLimitResult perMinute =
                rateLimiter.tryConsume(TIER_AUTH_MINUTE, clientKey, AUTH_REQUESTS_PER_MINUTE, MINUTE_SECS);
        RedisRateLimiter.RateLimitResult perHour =
                rateLimiter.tryConsume(TIER_AUTH_HOUR,   clientKey, AUTH_REQUESTS_PER_HOUR,   HOUR_SECS);

        // Expose the stricter of the two remaining counts
        long remaining = Math.min(perMinute.remaining(), perHour.remaining());
        response.setHeader(HEADER_LIMIT,     String.valueOf(AUTH_REQUESTS_PER_MINUTE));
        response.setHeader(HEADER_REMAINING, String.valueOf(remaining));

        if (!perMinute.allowed()) {
            return rejectRequest(clientKey, path, "AUTH/minute", perMinute.resetInSeconds(), response);
        }
        if (!perHour.allowed()) {
            return rejectRequest(clientKey, path, "AUTH/hour",   perHour.resetInSeconds(),   response);
        }
        return true;
    }

    private boolean handleApiTier(String clientKey, String path,
                                  HttpServletResponse response) throws IOException {

        RedisRateLimiter.RateLimitResult result =
                rateLimiter.tryConsume(TIER_API_MINUTE, clientKey, API_REQUESTS_PER_MINUTE, MINUTE_SECS);

        response.setHeader(HEADER_LIMIT,     String.valueOf(API_REQUESTS_PER_MINUTE));
        response.setHeader(HEADER_REMAINING, String.valueOf(result.remaining()));

        if (!result.allowed()) {
            return rejectRequest(clientKey, path, "API/minute", result.resetInSeconds(), response);
        }
        return true;
    }

    private boolean rejectRequest(String clientKey, String path, String tier,
                                  long resetSecs, HttpServletResponse response) throws IOException {
        long resetEpoch = System.currentTimeMillis() / 1000L + resetSecs;
        response.setHeader(HEADER_RESET,       String.valueOf(resetEpoch));
        response.setHeader(HEADER_RETRY_AFTER, String.valueOf(resetSecs));
        log.warn("[RATE_LIMIT] client={} path={} tier={} retryAfter={}s",
                clientKey, path, tier, resetSecs);
        response.sendError(429, "Rate limit exceeded. Retry after " + resetSecs + " second(s).");
        return false;
    }

    // ── Routing helpers ───────────────────────────────────────────────────────

    private boolean isAuthPath(String path) {
        if (path == null) return false;
        for (String prefix : AUTH_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    // ── Client key derivation ─────────────────────────────────────────────────

    /**
     * Derives a stable, non-guessable client key using a four-level priority:
     *
     * <ol>
     *   <li><b>X-Client-ID</b> — explicit SDK / mobile client identifier.</li>
     *   <li><b>Hashed Bearer token</b> — unique per session, not reversible.</li>
     *   <li><b>X-Forwarded-For first IP</b> — <em>only trusted when remoteAddr
     *       is within a configured trusted-proxy CIDR</em>, preventing header
     *       injection by arbitrary clients.</li>
     *   <li><b>remoteAddr</b> — the TCP peer address; always available.</li>
     * </ol>
     */
    private String extractClientKey(HttpServletRequest request) {
        // 1. Explicit client ID
        String clientId = request.getHeader("X-Client-ID");
        if (clientId != null && !clientId.isBlank()) {
            return "cid:" + clientId.trim();
        }

        // 2. Hashed Bearer token — unique per authenticated session, not reversible
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return "jwt:" + sha256Prefix(auth.substring(7));
        }

        // 3. X-Forwarded-For — ONLY if remoteAddr is a trusted proxy
        String remoteAddr = request.getRemoteAddr();
        if (isTrustedProxy(remoteAddr)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return "ip:" + forwarded.split(",")[0].trim();
            }
        }

        // 4. Direct TCP peer address
        return "ip:" + remoteAddr;
    }

    /**
     * Returns true if {@code remoteAddr} falls within any of the configured
     * trusted-proxy CIDRs.  Ignores any CIDRs that fail to parse rather than
     * crashing — a bad config entry causes a WARN, not a 500.
     */
    private boolean isTrustedProxy(String remoteAddr) {
        List<String> cidrs = config.getTrustedProxyCidrs();
        if (cidrs == null || cidrs.isEmpty()) {
            return false;
        }
        try {
            InetAddress addr = InetAddress.getByName(remoteAddr);
            for (String cidr : cidrs) {
                try {
                    if (isInCidr(addr, cidr)) return true;
                } catch (Exception e) {
                    log.warn("[RATE_LIMIT] Unparseable trusted-proxy CIDR '{}': {}", cidr, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[RATE_LIMIT] Could not parse remoteAddr '{}': {}", remoteAddr, e.getMessage());
        }
        return false;
    }

    /** Checks whether {@code addr} is within the given CIDR (e.g. "10.0.0.0/8"). */
    private boolean isInCidr(InetAddress addr, String cidr) throws Exception {
        int slash = cidr.indexOf('/');
        if (slash < 0) {
            // No prefix — treat as host address (/32 or /128)
            return InetAddress.getByName(cidr).equals(addr);
        }
        InetAddress network   = InetAddress.getByName(cidr.substring(0, slash));
        int         prefixLen = Integer.parseInt(cidr.substring(slash + 1));

        byte[] addrBytes    = addr.getAddress();
        byte[] networkBytes = network.getAddress();

        if (addrBytes.length != networkBytes.length) {
            // IPv4 vs IPv6 mismatch — not in CIDR
            return false;
        }

        int fullBytes  = prefixLen / 8;
        int remainBits = prefixLen % 8;

        for (int i = 0; i < fullBytes; i++) {
            if (addrBytes[i] != networkBytes[i]) return false;
        }
        if (remainBits > 0 && fullBytes < addrBytes.length) {
            int mask = (0xFF << (8 - remainBits)) & 0xFF;
            if ((addrBytes[fullBytes] & mask) != (networkBytes[fullBytes] & mask)) return false;
        }
        return true;
    }

    /** First 16 hex characters of SHA-256(token) — sufficient to identify a session. */
    private String sha256Prefix(String token) {
        try {
            MessageDigest md   = MessageDigest.getInstance("SHA-256");
            byte[]        hash = md.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb   = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always present in Java 8+ — unreachable in practice
            int len = token.length();
            return token.substring(Math.max(0, len - 16));
        }
    }
}
