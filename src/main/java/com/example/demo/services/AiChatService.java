package com.example.demo.services;

import com.example.demo.dto.AiChatRequest;
import com.example.demo.dto.AiChatResponse;

/**
 * Handles conversational AI queries grounded in live organisational data.
 *
 * Implementation uses a Retrieval-Augmented Generation (RAG) pattern:
 *   1. Retrieve  — fetch all relevant org data from the database
 *   2. Augment   — inject retrieved data into the LLM system prompt
 *   3. Generate  — call the Anthropic Messages API and return the response
 */
public interface AiChatService {

    /**
     * Process a chat message within the current tenant context.
     * The implementation retrieves all org data from the database and uses it
     * to ground the LLM's response before returning the answer.
     *
     * @param request the user's message, optional conversation ID, and recent history
     * @return the AI-generated response and conversation ID
     */
    AiChatResponse chat(AiChatRequest request);
}
