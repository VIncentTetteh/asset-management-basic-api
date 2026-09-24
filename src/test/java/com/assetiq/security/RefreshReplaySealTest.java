package com.assetiq.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Refresh replay envelope")
class RefreshReplaySealTest {

    private static final String CONSUMED = "wqB1p3f-consumed-token";
    private static final String REPLACEMENT = "Hk2s9Qa-replacement-token";

    @Test
    @DisplayName("only the holder of the consumed token can open the envelope")
    void onlyTheConsumedTokenOpensIt() {
        String sealed = RefreshReplaySeal.seal(CONSUMED, REPLACEMENT);

        assertThat(RefreshReplaySeal.unseal(CONSUMED, sealed)).isEqualTo(REPLACEMENT);
        assertThat(RefreshReplaySeal.unseal("some-other-token", sealed)).isNull();
    }

    @Test
    @DisplayName("the envelope carries no trace of either token")
    void theEnvelopeLeaksNothing() {
        String sealed = RefreshReplaySeal.seal(CONSUMED, REPLACEMENT);

        assertThat(sealed).doesNotContain(REPLACEMENT).doesNotContain(CONSUMED);
    }

    @Test
    @DisplayName("a tampered, truncated, foreign or absent envelope fails closed")
    void damagedEnvelopesFailClosed() {
        String sealed = RefreshReplaySeal.seal(CONSUMED, REPLACEMENT);
        String tampered = sealed.substring(0, sealed.length() - 4) + "AAAA";

        assertThat(RefreshReplaySeal.unseal(CONSUMED, tampered)).isNull();
        assertThat(RefreshReplaySeal.unseal(CONSUMED, sealed.substring(0, 12))).isNull();
        assertThat(RefreshReplaySeal.unseal(CONSUMED, "enc:v1:not-ours")).isNull();
        assertThat(RefreshReplaySeal.unseal(CONSUMED, null)).isNull();
        assertThat(RefreshReplaySeal.unseal(null, sealed)).isNull();
    }

    @Test
    @DisplayName("each sealing uses a fresh nonce")
    void sealingIsNotDeterministic() {
        assertThat(RefreshReplaySeal.seal(CONSUMED, REPLACEMENT))
                .isNotEqualTo(RefreshReplaySeal.seal(CONSUMED, REPLACEMENT));
    }
}
