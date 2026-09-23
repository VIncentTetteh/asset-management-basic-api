package com.assetiq.license.offline;

/**
 * Outcome of verifying a self-hosted offline licence key.
 *
 * <p>Only {@link #VALID} grants a paid tier. Every other value resolves to the
 * free tier — never to an outage. The distinction between them exists so the
 * operator gets an accurate message ("your key expired on X" reads very
 * differently from "no key is configured"), not so the application can behave
 * differently in each case.</p>
 */
public enum OfflineLicenseStatus {

    /** Signature verified against the build's public key and the key is in date. */
    VALID,

    /** No licence key was configured. The expected state for an evaluation install. */
    ABSENT,

    /** No public key is available in this build, so no key could ever verify. */
    NO_PUBLIC_KEY,

    /** Signature verified but the expiry has passed. */
    EXPIRED,

    /**
     * The signature did not verify: the payload was edited after signing, or the
     * key was signed by a different private key than this build trusts.
     */
    INVALID_SIGNATURE,

    /** The token is not a well-formed licence key, or is missing required claims. */
    MALFORMED;

    /** {@code true} when this status entitles the installation to its licensed tier. */
    public boolean isEntitled() {
        return this == VALID;
    }
}
