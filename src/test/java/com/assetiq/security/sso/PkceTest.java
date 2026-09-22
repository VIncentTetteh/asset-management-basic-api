package com.assetiq.security.sso;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PKCE (RFC 7636) verifier/challenge")
class PkceTest {

    /** RFC 7636 appendix B worked example. */
    private static final String RFC_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String RFC_CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @Test
    void challengeMatchesTheSpecificationsWorkedExample() {
        assertThat(Pkce.challengeFor(RFC_VERIFIER)).isEqualTo(RFC_CHALLENGE);
    }

    @Test
    void theRightVerifierMatches() {
        assertThat(Pkce.matches(RFC_CHALLENGE, RFC_VERIFIER)).isTrue();
    }

    @Test
    void aDifferentVerifierDoesNot() {
        assertThat(Pkce.matches(RFC_CHALLENGE, "x".repeat(43))).isFalse();
    }

    @Test
    void aVerifierOutsideTheAllowedLengthIsRejected() {
        assertThat(Pkce.matches(Pkce.challengeFor("short"), "short")).isFalse();
        String tooLong = "a".repeat(129);
        assertThat(Pkce.matches(Pkce.challengeFor(tooLong), tooLong)).isFalse();
    }

    @Test
    void nullsNeverMatch() {
        assertThat(Pkce.matches(null, RFC_VERIFIER)).isFalse();
        assertThat(Pkce.matches(RFC_CHALLENGE, null)).isFalse();
        assertThat(Pkce.matches("  ", RFC_VERIFIER)).isFalse();
    }

    @Test
    void aPaddedOrBase64AlphabetChallengeStillMatches() {
        assertThat(Pkce.matches(RFC_CHALLENGE + "=", RFC_VERIFIER)).isTrue();
        assertThat(Pkce.matches(RFC_CHALLENGE.replace('-', '+').replace('_', '/'), RFC_VERIFIER)).isTrue();
    }

    @Test
    void onlyS256IsSupportedAndAnOmittedMethodMeansS256() {
        assertThat(Pkce.isSupportedMethod("S256")).isTrue();
        assertThat(Pkce.isSupportedMethod("s256")).isTrue();
        assertThat(Pkce.isSupportedMethod(null)).isTrue();
        assertThat(Pkce.isSupportedMethod("")).isTrue();
        assertThat(Pkce.isSupportedMethod("plain")).isFalse();
        assertThat(Pkce.isSupportedMethod("PLAIN")).isFalse();
        assertThat(Pkce.isSupportedMethod("S512")).isFalse();
    }

    @Test
    void aChallengeMustLookLikeBase64Url() {
        assertThat(Pkce.isWellFormedChallenge(RFC_CHALLENGE)).isTrue();
        assertThat(Pkce.isWellFormedChallenge(null)).isFalse();
        assertThat(Pkce.isWellFormedChallenge("   ")).isFalse();
        assertThat(Pkce.isWellFormedChallenge("has spaces")).isFalse();
        assertThat(Pkce.isWellFormedChallenge("a".repeat(200))).isFalse();
    }
}
