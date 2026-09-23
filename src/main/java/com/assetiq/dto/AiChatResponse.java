package com.assetiq.dto;

import com.assetiq.services.ai.AiSource;

import java.util.List;

/**
 * Response body for POST /api/v1/ai/chat.
 *
 * <p>{@code message} and {@code conversationId} are unchanged from the original
 * contract; the rest is additive, so older clients keep working.
 *
 * @param message        the answer, grounded in the caller's own organisation
 * @param conversationId opaque id to send back on the next turn
 * @param sources        the records the answer was built from, so a user can verify it
 * @param scope          data sections the caller was permitted to read
 * @param withheld       sections withheld because the caller lacks the authority —
 *                       surfaced so the UI can say "ask an administrator", not so
 *                       the model can hint at the contents
 * @param degraded       true when the provider could not answer and {@code message}
 *                       is an explanation rather than an answer
 */
public record AiChatResponse(
        String message,
        String conversationId,
        List<AiSource> sources,
        List<String> scope,
        List<String> withheld,
        boolean degraded
) {
    /** Backwards-compatible constructor for callers that only have a message. */
    public AiChatResponse(String message, String conversationId) {
        this(message, conversationId, List.of(), List.of(), List.of(), false);
    }
}
