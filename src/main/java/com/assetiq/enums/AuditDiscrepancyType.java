package com.assetiq.enums;

/** What is wrong with an audited asset. */
public enum AuditDiscrepancyType {
    /** Expected at this location, not found anywhere. */
    MISSING,
    /** Found, but somewhere other than where the register says. */
    WRONG_LOCATION,
    /** Found, but not on the count sheet at all. */
    UNEXPECTED,
    /** Found where expected, but damaged. */
    DAMAGED
}
