package com.assetiq.license.offline;

import com.assetiq.enums.BillingPlanTier;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

/**
 * Verifies the offline licence key for a self-hosted AssetIQ installation.
 *
 * <p><strong>This bean does not exist unless {@code app.license.offline.enabled=true}.</strong>
 * The hosted deployment leaves the flag at its {@code false} default and is
 * therefore unaffected: {@code UsageLimitServiceImpl} receives
 * {@code Optional.empty()} and resolves entitlements from the Paystack
 * subscription exactly as before.</p>
 *
 * <h2>How it differs from {@link com.assetiq.license.LicenseService}</h2>
 * <p>{@code LicenseService} (the {@code APP_MODE=standalone} path) calls a vendor
 * licence server every 24 hours and blocks writes with HTTP 402 when it cannot
 * establish entitlement. This service does neither:</p>
 * <ul>
 *   <li><strong>No network, ever.</strong> The key is a self-contained signed
 *       document. Verification is a local RSA signature check against a public
 *       key baked into the build. An air-gapped installation works identically
 *       to a connected one, and nothing about the installation is reported
 *       anywhere.</li>
 *   <li><strong>No hard stop.</strong> Absent, expired, tampered or
 *       wrongly-signed keys all resolve to the free tier. The application keeps
 *       running, keeps serving, and keeps every row of the operator's data. The
 *       only consequence is that free-tier quotas apply until a current key is
 *       supplied.</li>
 * </ul>
 *
 * <h2>Key format</h2>
 * <p>An RS256 JWS whose claims are:</p>
 * <pre>
 *   sub    organisation name the key was issued to   (required)
 *   plan   tier: FREEMIUM | BASIC | BUSINESS | ENTERPRISE  (required)
 *   seats  integer seat entitlement                   (optional)
 *   exp    expiry                                     (required)
 * </pre>
 * <p>An unrecognised {@code plan} is treated as malformed rather than silently
 * upgraded or downgraded, so a typo in issuance never grants more than intended.</p>
 */
@Service
@ConditionalOnProperty(prefix = "app.license.offline", name = "enabled", havingValue = "true")
public class OfflineLicenseService {

    private static final Logger log = LoggerFactory.getLogger(OfflineLicenseService.class);

    /** Where the release build bakes the vendor's public key. */
    static final String BAKED_PUBLIC_KEY_RESOURCE = "/license/offline-public.pem";

    private final OfflineLicenseProperties props;
    private final Clock clock;

    /**
     * The trust anchor, or null when this build has none. Null is a supported
     * state: it resolves every key to the free tier rather than failing startup,
     * because a licence problem must never be an outage.
     */
    private RSAPublicKey publicKey;

    /**
     * Claims from the last successful signature verification, cached so the
     * RSA check runs once rather than on every entitlement lookup. Expiry is
     * re-evaluated against the clock on each call, so a key that lapses while
     * the process is running is honoured as expired without a restart.
     */
    private Claims verifiedClaims;

    /** Why verification failed, when it did. Null once {@link #verifiedClaims} is set. */
    private OfflineLicense verificationFailure;

    public OfflineLicenseService(OfflineLicenseProperties props) {
        this(props, Clock.systemUTC());
    }

    OfflineLicenseService(OfflineLicenseProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    @PostConstruct
    void init() {
        this.publicKey = loadPublicKey();
        verify();

        OfflineLicense state = current();
        if (state.isLicensed()) {
            log.info("[LICENCE] {}", state.message());
        } else {
            // Deliberately INFO, not WARN or ERROR. An unlicensed self-hosted
            // install is a supported configuration, not a fault to be paged on.
            log.info("[LICENCE] Running on the free tier. {}", state.message());
        }
    }

    /**
     * The entitlement in force right now.
     *
     * <p>Never null, never throws. Re-checks expiry on every call so the tier is
     * correct the moment a key lapses.</p>
     */
    public OfflineLicense current() {
        if (verifiedClaims == null) {
            return verificationFailure != null
                    ? verificationFailure
                    : OfflineLicense.freeTier(OfflineLicenseStatus.ABSENT, unlicensedMessage());
        }

        Instant expiry = verifiedClaims.getExpiration().toInstant();
        if (!clock.instant().isBefore(expiry)) {
            return OfflineLicense.freeTier(OfflineLicenseStatus.EXPIRED,
                    "Your AssetIQ licence expired on " + expiry + ". All of your data is intact and "
                            + "AssetIQ keeps running on free-tier limits. Supply a current licence key "
                            + "in APP_LICENSE_OFFLINE_KEY to restore your plan.");
        }

        return OfflineLicense.licensed(
                tierOf(verifiedClaims),
                verifiedClaims.getSubject(),
                seatsOf(verifiedClaims),
                expiry);
    }

    /** Convenience accessor: the tier to enforce right now. */
    public BillingPlanTier currentTier() {
        return current().tier();
    }

    /**
     * Narrows a plan's seat allowance to what the licence actually grants.
     *
     * <p>The licence is the tighter of the two bounds by design: a BUSINESS key
     * sold for 25 seats must not unlock the catalogue's 250-seat BUSINESS row.
     * Returns {@code planSeats} unchanged when the key carries no seat claim.</p>
     */
    public int effectiveSeatLimit(int planSeats) {
        Integer seats = current().seats();
        return seats == null ? planSeats : Math.min(planSeats, seats);
    }

    // ── Verification ─────────────────────────────────────────────────────────

    private void verify() {
        String key = props.getKey() == null ? "" : props.getKey().trim();

        if (publicKey == null) {
            verificationFailure = OfflineLicense.freeTier(OfflineLicenseStatus.NO_PUBLIC_KEY,
                    "This build carries no licence public key, so no licence key can be verified. "
                            + "AssetIQ is running normally on free-tier limits. See docs/self-hosting.md "
                            + "for how a licensed build is produced.");
            return;
        }

        if (key.isEmpty()) {
            verificationFailure = OfflineLicense.freeTier(OfflineLicenseStatus.ABSENT, unlicensedMessage());
            return;
        }

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .clock(() -> java.util.Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(key)
                    .getPayload();
        } catch (ExpiredJwtException expired) {
            // The signature verified; only the date has passed. Keep the claims so
            // current() can name the organisation and the expiry in its message
            // instead of reporting a generic failure.
            claims = expired.getClaims();
        } catch (JwtException | IllegalArgumentException e) {
            // Covers both a tampered payload and a key signed by a different
            // private key: in each case the RSA verification simply does not hold.
            verificationFailure = OfflineLicense.freeTier(OfflineLicenseStatus.INVALID_SIGNATURE,
                    "Your AssetIQ licence key did not verify against this build's licence public key. "
                            + "It may have been altered in transit, or issued for a different build. "
                            + "AssetIQ is running normally on free-tier limits — no data is affected. "
                            + "Ask your vendor to re-issue the key.");
            return;
        }

        if (claims.getExpiration() == null || claims.getSubject() == null || tierOrNull(claims) == null) {
            verificationFailure = OfflineLicense.freeTier(OfflineLicenseStatus.MALFORMED,
                    "Your AssetIQ licence key is correctly signed but is missing a required field "
                            + "(organisation, plan or expiry), or names a plan this version does not know. "
                            + "AssetIQ is running normally on free-tier limits. Ask your vendor to re-issue it.");
            return;
        }

        this.verifiedClaims = claims;
        this.verificationFailure = null;
    }

    private RSAPublicKey loadPublicKey() {
        String configured = props.getPublicKey();
        if (configured != null && !configured.isBlank()) {
            RSAPublicKey key = parsePem(configured, "app.license.offline.public-key");
            if (key != null) {
                return key;
            }
        }

        try (var in = OfflineLicenseService.class.getResourceAsStream(BAKED_PUBLIC_KEY_RESOURCE)) {
            if (in == null) {
                log.info("[LICENCE] No licence public key baked at classpath:{} — offline licensing "
                        + "will resolve to the free tier.", BAKED_PUBLIC_KEY_RESOURCE);
                return null;
            }
            return parsePem(new String(in.readAllBytes(), StandardCharsets.UTF_8),
                    "classpath:" + BAKED_PUBLIC_KEY_RESOURCE);
        } catch (Exception e) {
            log.warn("[LICENCE] Could not read the baked licence public key: {}", e.getMessage());
            return null;
        }
    }

    /** Returns null rather than throwing: a bad public key must not stop the app booting. */
    private RSAPublicKey parsePem(String pem, String source) {
        try {
            String body = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(body);
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            log.warn("[LICENCE] Licence public key at {} is not a readable RSA public key: {}",
                    source, e.getMessage());
            return null;
        }
    }

    // ── Claim helpers ────────────────────────────────────────────────────────

    private static BillingPlanTier tierOf(Claims claims) {
        BillingPlanTier tier = tierOrNull(claims);
        return tier == null ? BillingPlanTier.FREEMIUM : tier;
    }

    private static BillingPlanTier tierOrNull(Claims claims) {
        Object plan = claims.get("plan");
        if (!(plan instanceof String s) || s.isBlank()) {
            return null;
        }
        try {
            return BillingPlanTier.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknownTier) {
            return null;
        }
    }

    private static Integer seatsOf(Claims claims) {
        Object seats = claims.get("seats");
        return seats instanceof Number n ? n.intValue() : null;
    }

    private static String unlicensedMessage() {
        return "No licence key is configured, so AssetIQ is running on the free tier. "
                + "Everything works; free-tier quotas apply. Set APP_LICENSE_OFFLINE_KEY to "
                + "activate a purchased plan.";
    }
}
