package com.example.demo.dto;

import java.util.List;

/**
 * Request body for POST /api/v1/ai/chat.
 *
 * The client sends only the user's message and recent history.
 * All organisation context is built server-side from the database.
 */
public record AiChatRequest(

        /** The user's current message */
        String message,

        /**
         * Optional conversation ID for multi-turn continuity.
         * Returned by the first response and echoed on subsequent calls.
         */
        String conversationId,

        /**
         * Recent conversation turns for multi-turn context (last 20 max).
         * Each item has role ("user"|"assistant") and content.
         */
        List<ConversationMessage> history
) {}
