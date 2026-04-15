package com.assetiq.enums;

public enum LicenseType {
    PERPETUAL,      // One-time purchase, no expiry
    SUBSCRIPTION,   // Recurring (monthly/annual)
    VOLUME,         // Multi-seat volume license
    OPEN_SOURCE,    // Open source, tracked for compliance
    TRIAL,          // Time-limited evaluation
    ENTERPRISE,     // Enterprise agreement
    OEM             // Bundled with hardware
}
