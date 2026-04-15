package com.assetiq.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum POStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    DELIVERED,
    CANCELLED;

    @JsonCreator
    public static POStatus from(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("PENDING".equals(normalized)) {
            return SUBMITTED;
        }
        return POStatus.valueOf(normalized);
    }
}
