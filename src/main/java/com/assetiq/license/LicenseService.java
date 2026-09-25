package com.assetiq.license;

import com.assetiq.config.AppMode;
import com.assetiq.config.AppModeProperties;
import com.assetiq.config.ConditionalOnAppMode;
import com.assetiq.config.LicenseProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import com.assetiq.scheduling.RunsOnEveryInstance;

/**
 * Core license enforcement service — only loaded when {@code APP_MODE=standalone}.
 *
 * <p>Responsibilities:</p>
 * <ol>
 *   <li>Load and verify the RSA public key bundled at {@code classpath:license/public.pem}</li>
 *   <li>Parse the license JWT and verify its RSA-256 signature locally (fast, offline)</li>
 *   <li>Cache the current {@link LicenseState} in memory</li>
 *   <li>Every 24 h, call the License Server's {@code POST /v1/validate} endpoint to detect
 *       revocations and sync the latest plan state</li>
 *   <li>Track the grace period using the {@code last_remote_validation_at} timestamp stored
 *       in the DB — if the server is unreachable for longer than the grace period, the app
 *       transitions to read-only mode</li>
 * </ol>
 */
@Service
@ConditionalOnAppMode(AppMode.STANDALONE)
public class LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);
    private static final String PUBLIC_KEY_RESOURCE = "/license/public.pem";

    private final LicenseProperties props;
    private final LicenseSettingsRepository settingsRepo;
    private final ObjectMapper objectMapper;
    private final AppModeProperties appMode;

    /** Thread-safe in-memory cache of the current state. */
    private final AtomicReference<LicenseState> cachedState =
            new AtomicReference<>(LicenseState.error("License not yet validated."));

    private RSAPublicKey publicKey;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public LicenseService(LicenseProperties props,
                          LicenseSettingsRepository settingsRepo,
                          ObjectMapper objectMapper,
                          AppModeProperties appMode) {
        this.props       = props;
        this.settingsRepo = settingsRepo;
        this.objectMapper = objectMapper;
        this.appMode      = appMode;
    }

    // ── Startup ───────────────────────────────────────────────────────────────

    @PostConstruct
    void init() {
        log.info("LicenseService initialising (standalone mode)");
        loadPublicKey();
        refreshState();

        LicenseState state = cachedState.get();
        if (state.readOnly()) {
            log.warn("⚠  License state on startup: {} — {}", state.status(), state.message());
        } else {
            log.info("✓  License valid — plan={} expires={} daysRemaining={}",
                     state.plan(), state.expiresAt(), state.daysRemaining());
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns the current cached license state (never null). */
    public LicenseState getCurrentState() {
        return cachedState.get();
    }

    /**
     * Activates a new license key entered by the admin.
     * Validates the key locally, persists it to DB, then does an immediate
     * remote validation to confirm it hasn't been revoked.
     */
    @Transactional
    public LicenseState activateKey(String rawKey) {
        // 1. Verify signature locally first
        Claims claims;
        try {
            claims = parseJwt(rawKey.trim());
        } catch (JwtException e) {
            throw new IllegalArgumentException("Invalid license key: signature verification failed.");
        }

        if (claims.getExpiration().toInstant().isBefore(Instant.now())) {
            throw new IllegalArgumentException("This license key has already expired.");
        }

        // 2. Persist
        LicenseSettings settings = settingsRepo.findById(1L).orElse(new LicenseSettings());
        settings.setKeyToken(rawKey.trim());
        settingsRepo.save(settings);
        log.info("License key activated for org={}", claims.getSubject());

        // 3. Refresh state (includes immediate remote check)
        refreshState();
        return cachedState.get();
    }

    // ── Scheduled remote validation ───────────────────────────────────────────

    /** Runs every 24 hours. Validates the key against the License Server. */
    @Scheduled(fixedDelayString = "PT24H", initialDelayString = "PT24H")
    @RunsOnEveryInstance(reason =
            "refreshState() writes to the per-JVM cachedState AtomicReference that this "
            + "instance serves every license check from. Locking it would let one replica "
            + "refresh while all others kept stale state indefinitely — including, after a "
            + "renewal or revocation, the wrong answer. The work is a read-only remote "
            + "validation with no shared side effects, so running it on every replica is "
            + "both correct and cheap.")
    public void scheduledRemoteValidation() {
        log.debug("Running scheduled remote license validation");
        refreshState();
    }

    // ── Core logic ────────────────────────────────────────────────────────────

    void refreshState() {
        String keyToken = resolveKeyToken();
        if (keyToken == null || keyToken.isBlank()) {
            cachedState.set(LicenseState.error(
                "No license key configured. Enter your key at Settings → License."));
            return;
        }

        // Local RSA verification (fast, offline)
        Claims claims;
        try {
            claims = parseJwt(keyToken);
        } catch (JwtException e) {
            cachedState.set(LicenseState.error("License key signature is invalid."));
            return;
        }

        // Remote validation (may fail if server is unreachable)
        LicenseState remoteState = callLicenseServer(keyToken);
        if (remoteState != null) {
            cachedState.set(remoteState);
            persistRemoteValidationTimestamp();
            return;
        }

        // Remote call failed — fall back to local JWT claims + grace period check
        cachedState.set(buildStateFromLocalClaims(claims));
    }

    // ── Remote call ───────────────────────────────────────────────────────────

    private LicenseState callLicenseServer(String keyToken) {
        try {
            String instanceId = buildInstanceId();
            Map<String, Object> body = Map.of(
                "key",        keyToken,
                "instanceId", instanceId,
                "appVersion", "2.0.0"
            );
            String json = objectMapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(props.getServerUrl() + "/v1/validate"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            // Persist cache regardless of response code
            settingsRepo.findById(1L).ifPresent(s -> {
                s.setRemoteValidationCache(resp.body());
                settingsRepo.save(s);
            });

            Map<String, Object> data = objectMapper.readValue(
                resp.body(), new TypeReference<Map<String, Object>>() {});

            return mapServerResponse(data);
        } catch (Exception e) {
            log.warn("Remote license validation failed: {} — using grace period fallback", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private LicenseState mapServerResponse(Map<String, Object> data) {
        String status       = (String) data.get("status");
        String plan         = (String) data.get("plan");
        String expiresAtStr = (String) data.get("expiresAt");
        Instant expiresAt   = expiresAtStr != null ? Instant.parse(expiresAtStr) : null;
        long daysRemaining  = data.get("daysRemaining") instanceof Number n ? n.longValue() : 0;
        int graceDays       = data.get("gracePeriodDays") instanceof Number n ? n.intValue() : 7;
        Map<String,Object> limits   = (Map<String,Object>) data.getOrDefault("limits",   Map.of());
        Map<String,Object> features = (Map<String,Object>) data.getOrDefault("features", Map.of());
        String message      = (String) data.get("message");
        Instant now         = Instant.now();

        return switch (status) {
            case "valid"         -> LicenseState.valid(plan, expiresAt, daysRemaining,
                                                       graceDays, limits, features, now);
            case "grace_period"  -> LicenseState.gracePeriod(plan, expiresAt, daysRemaining,
                                                              graceDays, limits, features, now, message);
            case "expired"       -> LicenseState.expired(message);
            case "revoked"       -> LicenseState.revoked(message);
            default              -> LicenseState.error(message != null ? message : "Unknown license status: " + status);
        };
    }

    // ── Local fallback ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private LicenseState buildStateFromLocalClaims(Claims claims) {
        Instant expiresAt       = claims.getExpiration().toInstant();
        Map<String,Object> limits   = (Map<String,Object>) claims.getOrDefault("limits",   Map.of());
        Map<String,Object> features = (Map<String,Object>) claims.getOrDefault("features", Map.of());
        String plan             = (String) claims.get("plan");
        int graceDays           = claims.get("grace_period_days") instanceof Number n ? n.intValue() : 7;
        long daysRemaining      = Duration.between(Instant.now(), expiresAt).toDays();
        Instant lastRemote      = getLastRemoteValidationAt();

        // Key not yet expired
        if (Instant.now().isBefore(expiresAt)) {
            if (lastRemote == null) {
                // Never successfully validated remotely — conservative: allow up to 1 day
                boolean withinInitialGrace = Duration.between(
                    getSettingsUpdatedAt(), Instant.now()).toHours() < 24;
                return withinInitialGrace
                    ? LicenseState.valid(plan, expiresAt, daysRemaining, graceDays, limits, features, null)
                    : LicenseState.error("Cannot reach license server. Check your internet connection.");
            }
            // Was validated before — use grace period from last successful check
            long hoursSinceValidation = Duration.between(lastRemote, Instant.now()).toHours();
            long gracePeriodHours     = (long) graceDays * 24;
            return hoursSinceValidation < gracePeriodHours
                ? LicenseState.valid(plan, expiresAt, daysRemaining, graceDays, limits, features, lastRemote)
                : LicenseState.error("Cannot reach license server. Grace period exceeded — contact support.");
        }

        // Key is past its expiry date
        if (lastRemote != null) {
            Instant graceEnd = expiresAt.plusSeconds((long) graceDays * 86400);
            if (Instant.now().isBefore(graceEnd)) {
                long graceDaysLeft = Duration.between(Instant.now(), graceEnd).toDays();
                return LicenseState.gracePeriod(plan, expiresAt, 0,
                    graceDays, limits, features, lastRemote,
                    "License expired. " + graceDaysLeft + " grace day(s) remaining.");
            }
        }
        return LicenseState.expired("License has expired. Renew at https://portal.assetiq.io");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Claims parseJwt(String token) {
        return Jwts.parser()
                   .verifyWith(publicKey)
                   .build()
                   .parseSignedClaims(token)
                   .getPayload();
    }

    private void loadPublicKey() {
        try (var is = getClass().getResourceAsStream(PUBLIC_KEY_RESOURCE)) {
            if (is == null) {
                log.error("RSA public key not found at classpath:license/public.pem — " +
                          "copy your public.pem from the License Server into src/main/resources/license/");
                throw new IllegalStateException("Missing license public key");
            }
            String pem = new String(is.readAllBytes(), StandardCharsets.UTF_8)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(pem);
            KeyFactory kf  = KeyFactory.getInstance("RSA");
            publicKey = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(decoded));
            log.info("RSA public key loaded for license verification");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA public key: " + e.getMessage(), e);
        }
    }

    private String resolveKeyToken() {
        // DB takes priority (set via activate endpoint); fall back to env/property
        return settingsRepo.findById(1L)
                           .map(LicenseSettings::getKeyToken)
                           .filter(t -> t != null && !t.isBlank())
                           .orElse(props.getKey());
    }

    private Instant getLastRemoteValidationAt() {
        return settingsRepo.findById(1L)
                           .map(LicenseSettings::getLastRemoteValidationAt)
                           .orElse(null);
    }

    private Instant getSettingsUpdatedAt() {
        return settingsRepo.findById(1L)
                           .map(LicenseSettings::getUpdatedAt)
                           .orElse(Instant.now());
    }

    @Transactional
    void persistRemoteValidationTimestamp() {
        LicenseSettings s = settingsRepo.findById(1L).orElse(new LicenseSettings());
        s.setLastRemoteValidationAt(Instant.now());
        settingsRepo.save(s);
    }

    private String buildInstanceId() {
        try {
            String hostname = java.net.InetAddress.getLocalHost().getHostName();
            var interfaces  = java.net.NetworkInterface.getNetworkInterfaces();
            StringBuilder sb = new StringBuilder(hostname);
            while (interfaces.hasMoreElements()) {
                var ni  = interfaces.nextElement();
                var mac = ni.getHardwareAddress();
                if (mac != null) {
                    for (byte b : mac) sb.append(String.format("%02X", b));
                    break;
                }
            }
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "sha256:unknown-" + UUID.randomUUID();
        }
    }
}
