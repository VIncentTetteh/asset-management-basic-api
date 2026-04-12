package com.example.demo.dto;

/**
 * A single turn in a conversation with the AI assistant.
 * role must be either "user" or "assistant".
 */
public record ConversationMessage(String role, String content) {}
