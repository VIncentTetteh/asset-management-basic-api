package com.assetiq.license.offline;

import com.assetiq.enums.BillingPlanTier;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline licence verification.
 *
 * <p>Every case asserts the <em>tier that results</em>, because that is the only
 * thing the rest of the application acts on. The governing rule under test is
 * that no licence problem is ever an outage: absent, expired, tampered and
 * wrongly-signed keys all land on {@link BillingPlanTier#FREEMIUM} with a
 * message an operator can act on, and none of them throws.</p>
 *
 * <p>Keys are generated in-process. No key material is committed to the repo and
 * no test reaches the network — which is also the property the feature promises.</p>
 */
class OfflineLicenseServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
    private static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);

    /** The keypair the build trusts. */
    private static KeyPair trusted;
    /** A keypair the build does NOT trust — stands in for a forged issuer. */
    private static KeyPair untrusted;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        trusted = gen.generateKeyPair();
        untrusted = gen.generateKeyPair();
    }

    // ── The five required cases ──────────────────────────────────────────────

    @Test
    @DisplayName("valid key grants exactly the tier it names")
    void validKeyGrantsItsTier() {
        OfflineLicense licence = evaluate(
                sign(trusted, "Acme Bank Ltd", "BUSINESS", 25, NOW.plus(Duration.ofDays(90))),
                pem(trusted));

        assertThat(licence.status()).isEqualTo(OfflineLicenseStatus.VALID);
        assertThat(licence.tier()).isEqualTo(BillingPlanTier.BUSINESS);
        assertThat(licence.organisation()).isEqualTo("Acme Bank Ltd");
        assertThat(licence.seats()).isEqualTo(25);
        assertThat(licence.isLicensed()).isTrue();
    }

    @Test
    @DisplayName("expired key falls back to FREEMIUM and says when it lapsed")
    void expiredKeyFallsBackToFreemium() {
        Instant lapsed = NOW.minus(Duration.ofDays(3));
        OfflineLicense licence = evaluate(
                sign(trusted, "Acme Bank Ltd", "ENTERPRISE", 500, lapsed),
                pem(trusted));

        assertThat(licence.status()).isEqualTo(OfflineLicenseStatus.EXPIRED);
        assertThat(licence.tier()).isEqualTo(BillingPlanTier.FREEMIUM);
        assertThat(licence.isLicensed()).isFalse();
        assertThat(licence.message())
                .contains(lapsed.toString())
                .contains("data is intact");
    }

    @Test
    @DisplayName("tampered payload falls back to FREEMIUM, not to the tier it claims")
    void tamperedSignatureFallsBackToFreemium() {
        String issued = sign(trusted, "Acme Bank Ltd", "BASIC", 10, NOW.plus(Duration.ofDays(90)));

        // Re-encode the payload segment claiming ENTERPRISE while keeping the
        // original signature — the classic privilege-escalation attempt.
        String[] parts = issued.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                new String(Base64.getUrlDecoder().decode(parts[1]))
                        .replace("\"BASIC\"", "\"ENTERPRISE\"")
                        .getBytes());
        String tampered = parts[0] + "." + forgedPayload + "." + parts[2];

        OfflineLicense licence = evaluate(tampered, pem(trusted));

        assertThat(licence.status()).isEqualTo(OfflineLicenseStatus.INVALID_SIGNATURE);
        assertThat(licence.tier()).isEqualTo(BillingPlanTier.FREEMIUM);
        assertThat(licence.isLicensed()).isFalse();
    }

    @Test
    @DisplayName("key signed by an untrusted issuer falls back to FREEMIUM")
    void wrongPublicKeyFallsBackToFreemium() {
        // Perfectly well-formed and in date — but signed by a private key this
        // build's public key does not correspond to.
        OfflineLicense licence = evaluate(
                sign(untrusted, "Acme Bank Ltd", "ENTERPRISE", 500, NOW.plus(Duration.ofDays(365))),
                pem(trusted));

        assertThat(licence.status()).isEqualTo(OfflineLicenseStatus.INVALID_SIGNATURE);
        assertThat(licence.tier()).isEqualTo(BillingPlanTier.FREEMIUM);
        assertThat(licence.isLicensed()).isFalse();
    }

    @Test
    @DisplayName("absent key falls back to FREEMIUM with a non-punitive message")
    void absentKeyFallsBackToFreemium() {
        OfflineLicense licence = evaluate("", pem(trusted));

        assertThat(licence.status()).isEqualTo(OfflineLicenseStatus.ABSENT);
        assertThat(licence.tier()).isEqualTo(BillingPlanTier.FREEMIUM);
        assertThat(licence.isLicensed()).isFalse();
        assertThat(licence.message()).contains("Everything works");
    }

    // ── Supporting cases ─────────────────────────────────────────────────────

    @Test
    @DisplayName("a build with no public key at all still starts, on FREEMIUM")
    void noPublicKeyFallsBackToFreemium() {
        // No property override and no baked classpath resource in the test build.
        OfflineLicense licence = evaluate(
                sign(trusted, "Acme Bank Ltd", "BUSINESS", 25, NOW.plus(Duration.ofDays(90))),
                "");

        assertThat(licence.status()).isEqualTo(OfflineLicenseStatus.NO_PUBLIC_KEY);
        assertThat(licence.tier()).isEqualTo(BillingPlanTier.FREEMIUM);
    }

    @Test
    @DisplayName("a signed key naming an unknown plan is malformed, not an upgrade")
    void unknownPlanIsMalformed() {
        OfflineLicense licence = evaluate(
                sign(trusted, "Acme Bank Ltd", "PLATINUM_UNLIMITED", 9999, NOW.plus(Duration.ofDays(90))),
                pem(trusted));

        assertThat(licence.status()).isEqualTo(OfflineLicenseStatus.MALFORMED);
        assertThat(licence.tier()).isEqualTo(BillingPlanTier.FREEMIUM);
    }

    @Test
    @DisplayName("a garbage string is rejected without throwing")
    void garbageKeyIsRejected() {
        OfflineLicense licence = evaluate("not-a-licence-key", pem(trusted));

        assertThat(licence.status()).isEqualTo(OfflineLicenseStatus.INVALID_SIGNATURE);
        assertThat(licence.tier()).isEqualTo(BillingPlanTier.FREEMIUM);
    }

    @Test
    @DisplayName("seat limit is the tighter of the plan allowance and the licence")
    void seatLimitIsNarrowedByTheLicence() {
        OfflineLicenseService service = service(
                sign(trusted, "Acme Bank Ltd", "BUSINESS", 25, NOW.plus(Duration.ofDays(90))),
                pem(trusted));

        assertThat(service.effectiveSeatLimit(250)).isEqualTo(25);   // licence is tighter
        assertThat(service.effectiveSeatLimit(10)).isEqualTo(10);    // plan is tighter
    }

    @Test
    @DisplayName("a key with no seat claim leaves the plan allowance untouched")
    void seatLimitUnchangedWithoutASeatClaim() {
        OfflineLicenseService service = service(
                sign(trusted, "Acme Bank Ltd", "BUSINESS", null, NOW.plus(Duration.ofDays(90))),
                pem(trusted));

        assertThat(service.current().status()).isEqualTo(OfflineLicenseStatus.VALID);
        assertThat(service.effectiveSeatLimit(250)).isEqualTo(250);
    }

    @Test
    @DisplayName("an unlicensed installation is capped at the free plan's own seats")
    void unlicensedSeatLimitIsThePlanAllowance() {
        OfflineLicenseService service = service("", pem(trusted));

        assertThat(service.effectiveSeatLimit(5)).isEqualTo(5);
    }

    @Test
    @DisplayName("a key that lapses while running is honoured as expired without a restart")
    void expiryIsReEvaluatedAgainstTheClock() {
        Instant expiry = NOW.plus(Duration.ofDays(1));
        String key = sign(trusted, "Acme Bank Ltd", "BUSINESS", 25, expiry);

        OfflineLicenseService live = service(key, pem(trusted), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(live.currentTier()).isEqualTo(BillingPlanTier.BUSINESS);

        // Same verified key, clock advanced past the expiry.
        OfflineLicenseService later = service(key, pem(trusted),
                Clock.fixed(expiry.plus(Duration.ofSeconds(1)), ZoneOffset.UTC));
        assertThat(later.currentTier()).isEqualTo(BillingPlanTier.FREEMIUM);
        assertThat(later.current().status()).isEqualTo(OfflineLicenseStatus.EXPIRED);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static OfflineLicense evaluate(String key, String publicKeyPem) {
        return service(key, publicKeyPem).current();
    }

    private static OfflineLicenseService service(String key, String publicKeyPem) {
        return service(key, publicKeyPem, FIXED);
    }

    private static OfflineLicenseService service(String key, String publicKeyPem, Clock clock) {
        OfflineLicenseProperties props = new OfflineLicenseProperties();
        props.setEnabled(true);
        props.setKey(key);
        props.setPublicKey(publicKeyPem);

        OfflineLicenseService service = new OfflineLicenseService(props, clock);
        service.init();
        return service;
    }

    /** Issues a licence exactly as the vendor signing script does. */
    private static String sign(KeyPair issuer, String org, String plan, Integer seats, Instant expiry) {
        var builder = Jwts.builder()
                .subject(org)
                .issuer("assetiq-license")
                .issuedAt(Date.from(NOW.minus(Duration.ofDays(1))))
                .expiration(Date.from(expiry))
                .claims(seats == null ? Map.of("plan", plan) : Map.of("plan", plan, "seats", seats));
        return builder.signWith((RSAPrivateKey) issuer.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private static String pem(KeyPair pair) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes())
                        .encodeToString(((RSAPublicKey) pair.getPublic()).getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
    }
}
