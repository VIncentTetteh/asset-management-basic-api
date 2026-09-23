package com.assetiq.services.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP implementation for the three providers AssetIQ supports.
 *
 * <ul>
 *   <li>{@code groq}      — OpenAI-compatible, free tier (default)</li>
 *   <li>{@code anthropic} — Messages API</li>
 *   <li>{@code ollama}    — local, OpenAI-compatible, no key</li>
 * </ul>
 *
 * <p>Every failure path here raises {@link LlmUnavailableException} with a reason.
 * Nothing escapes as a raw runtime exception, because a provider having a bad day
 * is not a server error on our side and must not read like one to a user.
 *
 * <p>Neither the prompt nor the reply is ever logged: both contain tenant data.
 * What is logged is the provider, the status code and the response size.
 */
@Component
public class HttpLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(HttpLlmClient.class);

    private static final String PROVIDER_ANTHROPIC = "anthropic";
    private static final String PROVIDER_GROQ      = "groq";
    private static final String PROVIDER_OLLAMA    = "ollama";

    private static final String ANTHROPIC_URL     = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String GROQ_URL          = "https://api.groq.com/openai/v1/chat/completions";

    /** Deterministic ceiling on the reply, so one answer cannot exhaust the quota. */
    private static final int MAX_TOKENS = 1200;

    /** A user waiting on a chat box will not wait a minute. Fail before they give up. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);

    private final HttpClient   httpClient;
    private final ObjectMapper objectMapper;

    @Value("${ai.provider:groq}")
    private String provider;

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    @Value("${anthropic.api.key:}")
    private String anthropicApiKey;

    @Value("${anthropic.model:claude-haiku-4-5-20251001}")
    private String anthropicModel;

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:llama3.1:8b}")
    private String ollamaModel;

    public HttpLlmClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient   = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String provider() {
        return provider == null ? "" : provider.toLowerCase();
    }

    @Override
    public boolean isConfigured() {
        return switch (provider()) {
            case PROVIDER_GROQ      -> hasText(groqApiKey);
            case PROVIDER_ANTHROPIC -> hasText(anthropicApiKey);
            case PROVIDER_OLLAMA    -> hasText(ollamaBaseUrl);
            default -> false;
        };
    }

    @Override
    public String complete(String systemPrompt, List<Map<String, String>> messages) {
        if (!isConfigured()) {
            throw new LlmUnavailableException(LlmUnavailableException.Reason.NOT_CONFIGURED,
                    "No credentials configured for AI provider '" + provider() + "'.");
        }
        return switch (provider()) {
            case PROVIDER_ANTHROPIC -> callAnthropic(systemPrompt, messages);
            case PROVIDER_GROQ      -> callOpenAiCompatible(
                    GROQ_URL, "Bearer " + groqApiKey, groqModel, systemPrompt, messages);
            case PROVIDER_OLLAMA    -> callOpenAiCompatible(
                    ollamaBaseUrl + "/v1/chat/completions", null, ollamaModel, systemPrompt, messages);
            default -> throw new LlmUnavailableException(LlmUnavailableException.Reason.NOT_CONFIGURED,
                    "Unknown ai.provider '" + provider() + "'. Valid values: groq, anthropic, ollama.");
        };
    }

    // ── Providers ────────────────────────────────────────────────────────────

    private String callAnthropic(String systemPrompt, List<Map<String, String>> messages) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model",      anthropicModel);
        body.put("max_tokens", MAX_TOKENS);
        body.put("system",     systemPrompt);
        body.put("messages",   messages);

        JsonNode json = send(ANTHROPIC_URL, body, builder -> builder
                .header("x-api-key", anthropicApiKey)
                .header("anthropic-version", ANTHROPIC_VERSION));
        return json.path("content").path(0).path("text").asText("");
    }

    private String callOpenAiCompatible(String url, String authorization, String model,
                                        String systemPrompt, List<Map<String, String>> messages) {
        List<Map<String, String>> all = new ArrayList<>();
        all.add(Map.of("role", "system", "content", systemPrompt));
        all.addAll(messages);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model",      model);
        body.put("max_tokens", MAX_TOKENS);
        body.put("messages",   all);

        JsonNode json = send(url, body, builder -> {
            if (authorization != null) {
                builder.header("Authorization", authorization);
            }
            return builder;
        });
        return json.path("choices").path(0).path("message").path("content").asText("");
    }

    // ── Transport ────────────────────────────────────────────────────────────

    private JsonNode send(String url, Map<String, Object> body,
                          java.util.function.UnaryOperator<HttpRequest.Builder> headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));

            HttpResponse<String> response =
                    httpClient.send(headers.apply(builder).build(), HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status == 200) {
                return objectMapper.readTree(response.body());
            }
            // The body can echo the prompt back, so it is never logged.
            log.warn("[AI] provider={} returned status={}", provider(), status);
            if (status == 429) {
                throw new LlmUnavailableException(LlmUnavailableException.Reason.PROVIDER_THROTTLED,
                        "AI provider is rate limiting requests.");
            }
            if (status >= 500) {
                throw new LlmUnavailableException(LlmUnavailableException.Reason.UNREACHABLE,
                        "AI provider returned " + status + ".");
            }
            throw new LlmUnavailableException(LlmUnavailableException.Reason.REJECTED,
                    "AI provider rejected the request with status " + status + ".");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmUnavailableException(LlmUnavailableException.Reason.UNREACHABLE,
                    "AI request was interrupted.", e);
        } catch (IOException e) {
            log.warn("[AI] provider={} unreachable: {}", provider(), e.getClass().getSimpleName());
            throw new LlmUnavailableException(LlmUnavailableException.Reason.UNREACHABLE,
                    "AI provider is unreachable.", e);
        } catch (LlmUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[AI] provider={} call failed: {}", provider(), e.getClass().getSimpleName());
            throw new LlmUnavailableException(LlmUnavailableException.Reason.UNREACHABLE,
                    "AI request failed.", e);
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
