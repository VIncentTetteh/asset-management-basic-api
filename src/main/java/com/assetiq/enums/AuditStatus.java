package com.assetiq.enums;

public enum AuditStatus {
    PLANNED,
    IN_PROGRESS,
    COMPLETED,
    DISCREPANCY_FOUND,
    RESOLVED,
    /** Called off before completion (the web client already offered it). */
    CANCELLED
}

