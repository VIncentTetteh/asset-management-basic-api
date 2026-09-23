package com.assetiq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for POST /api/v1/ai/chat.
 *
 * <p>The client sends only the user's message and recent history. All
 * organisation context is retrieved server-side, scoped to the caller's tenant
 * and to what the caller is permitted to read — a client cannot widen it.
 *
 * @param message        the user's current message
 * @param conversationId opaque id returned by the previous response; echo it back
 *                       for multi-turn continuity
 * @param history        recent turns. Anything beyond the newest
 *                       {@code MAX_HISTORY_TURNS} is dropped server-side.
 */
public record AiChatRequest(

        @NotBlank(message = "message is required")
        @Size(max = 2000, message = "message must be 2000 characters or fewer")
        String message,

        @Size(max = 64, message = "conversationId must be 64 characters or fewer")
        String conversationId,

        List<ConversationMessage> history
) {}
