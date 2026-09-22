package com.assetiq.enums;

/** Where one line of an audit count sheet has got to. */
public enum AuditItemStatus {
    /** Generated, not yet sighted. */
    PENDING,
    /** Sighted where it was expected, in the condition recorded. */
    VERIFIED,
    /** Sighted or not sighted, but something is wrong — see the discrepancy type. */
    DISCREPANCY
}
