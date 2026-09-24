package com.assetiq.services.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Provider selection, without calling anybody's API.
 *
 * The assistant degrades rather than failing, so a provider that is merely
 * unknown must still be distinguishable from one that is configured — otherwise
 * a typo in {@code ai.provider} looks like a working assistant that answers
 * nothing.
 */
@DisplayName("HttpLlmClient provider selection")
class HttpLlmClientProviderTest {

    private HttpLlmClient client(String provider, String field, String value) {
        HttpLlmClient client = new HttpLlmClient(new ObjectMapper());
        ReflectionTestUtils.setField(client, "provider", provider);
        if (field != null) {
            ReflectionTestUtils.setField(client, field, value);
        }
        return client;
    }

    @Test
    @DisplayName("xai counts as configured once its key is present, and not before")
    void xaiIsConfiguredWithAKey() {
        assertThat(client("xai", null, null).isConfigured()).isFalse();
        assertThat(client("xai", "xaiApiKey", "test-key-not-a-real-credential").isConfigured()).isTrue();
    }

    @Test
    @DisplayName("provider is reported case-insensitively, so XAI and xai are one provider")
    void providerIsCaseInsensitive() {
        assertThat(client("XAI", "xaiApiKey", "test-key-not-a-real-credential").provider()).isEqualTo("xai");
        assertThat(client("XAI", "xaiApiKey", "test-key-not-a-real-credential").isConfigured()).isTrue();
    }

    @Test
    @DisplayName("an unknown provider is never configured, and says so by name")
    void unknownProviderIsNotConfigured() {
        HttpLlmClient client = client("grok", "xaiApiKey", "test-key-not-a-real-credential");
        assertThat(client.isConfigured()).isFalse();

        // Not configured is the reason a caller sees; the message names the typo
        // so the operator can fix it rather than hunting a silent no-op.
        assertThatThrownBy(() -> client.complete("system", List.of(Map.of("role", "user", "content", "hi"))))
                .isInstanceOf(LlmUnavailableException.class)
                .hasMessageContaining("grok");
    }

    @Test
    @DisplayName("the other providers still decide on their own credentials")
    void existingProvidersUnchanged() {
        assertThat(client("groq", "groqApiKey", "test-key-not-a-real-credential").isConfigured()).isTrue();
        assertThat(client("anthropic", "anthropicApiKey", "test-key-not-a-real-credential").isConfigured()).isTrue();
        assertThat(client("groq", "xaiApiKey", "test-key-not-a-real-credential").isConfigured()).isFalse();
    }
}
