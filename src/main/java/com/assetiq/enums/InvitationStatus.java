package com.assetiq.enums;

/**
 * Lifecycle of a colleague invitation.
 *
 * <p>{@link #EXPIRED} is never written to the database — expiry is a fact about
 * the clock, not a state anyone transitions into, so a row stays
 * {@link #PENDING} and is reported as expired once {@code expiresAt} has passed.
 * Storing it would mean a sweeper job whose lateness changes what the API says.
 */
public enum InvitationStatus {

    /** Sent, not yet redeemed, and still inside its validity window. */
    PENDING,

    /** Redeemed: the account exists and the token no longer works. */
    ACCEPTED,

    /** Withdrawn by an administrator before it was redeemed. */
    REVOKED,

    /** Derived, never persisted: still PENDING but past {@code expiresAt}. */
    EXPIRED
}
