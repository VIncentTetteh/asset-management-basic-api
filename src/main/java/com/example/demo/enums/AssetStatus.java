package com.example.demo.enums;

public enum AssetStatus {
    PENDING_PROCUREMENT, // PO raised, not yet received
    IN_STOCK, // Received, not yet assigned
    RESERVED, // Earmarked for assignment
    IN_USE, // Assigned and actively used
    MAINTENANCE, // Scheduled maintenance
    UNDER_REPAIR, // With vendor / undergoing repair
    RETIRED, // End-of-life, no longer in use
    DISPOSED, // Disposed / sold / scrapped
    MISSING // Cannot be located
}
