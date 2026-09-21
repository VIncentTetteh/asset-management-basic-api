package com.assetiq.enums;

/** Maker-checker lifecycle of a disposal (V44). */
public enum DisposalStatus {
    /** Requested; the asset is not disposed yet. */
    PENDING_APPROVAL,
    /** Approved by a user other than the requester; the asset is DISPOSED. */
    APPROVED,
    /** Refused or withdrawn; the asset is untouched. */
    REJECTED
}
