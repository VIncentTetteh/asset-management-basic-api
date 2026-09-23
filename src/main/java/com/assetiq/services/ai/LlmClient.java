package com.assetiq.services.ai;

import java.util.List;
import java.util.Map;

/**
 * The one place that talks to a model provider. Split out from the chat service
 * so the orchestration above it — scoping, citation, degradation — can be tested
 * without a network, and so a provider swap touches nothing else.
 */
public interface LlmClient {

    /** True when this environment has everything the configured provider needs. */
    boolean isConfigured();

    /** The configured provider id, e.g. {@code groq}. Never a key or a URL. */
    String provider();

    /**
     * Sends one completion request.
     *
     * @param systemPrompt the grounding prompt
     * @param messages     ordered {@code role}/{@code content} pairs
     * @return the model's reply text
     * @throws LlmUnavailableException when the provider refused, timed out or is unconfigured
     */
    String complete(String systemPrompt, List<Map<String, String>> messages);
}
