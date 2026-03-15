package com.example.demo.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Logback filter that filters out log messages containing sensitive information.
 * Note: This is a simple filter that rejects messages; a more sophisticated
 * implementation would redact sensitive data using a custom encoder.
 */
public class SensitiveDataFilter extends Filter<ILoggingEvent> {

    private static final String[] SENSITIVE_KEYWORDS = {
        "password", "passwd", "pwd",
        "token", "authorization", "bearer",
        "api_key", "apikey", "secret",
        "credit_card", "ccn",
        "ssn", "otp", "mfa"
    };

    @Override
    public FilterReply decide(ILoggingEvent event) {
        String message = event.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            for (String keyword : SENSITIVE_KEYWORDS) {
                if (lowerMessage.contains(keyword)) {
                    // Reject messages containing sensitive keywords
                    return FilterReply.DENY;
                }
            }
        }
        return FilterReply.ACCEPT;
    }
}


