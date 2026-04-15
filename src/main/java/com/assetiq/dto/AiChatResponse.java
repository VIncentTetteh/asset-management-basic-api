package com.assetiq.dto;

/**
 * Response body for POST /api/v1/ai/chat.
 */
public record AiChatResponse(

        /** The AI-generated answer grounded in the organisation's data */
        String message,

        /**
         * Conversation ID to pass back on the next request for multi-turn continuity.
         * Clients should treat this as opaque.
         */
        String conversationId
) {}
