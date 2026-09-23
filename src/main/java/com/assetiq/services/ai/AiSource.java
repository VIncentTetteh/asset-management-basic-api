package com.assetiq.services.ai;

/**
 * One record the assistant was shown, echoed back to the caller so a human can
 * open it and check the answer.
 *
 * @param type  record type, e.g. {@code ASSET}, {@code CONTRACT}
 * @param ref   the identifier a user would recognise (asset tag, contract number,
 *              control reference) — never a raw surrogate key where a business
 *              identifier exists
 * @param label short human-readable name
 */
public record AiSource(String type, String ref, String label) {
}
