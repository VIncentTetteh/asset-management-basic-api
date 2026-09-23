package com.assetiq.services.ai;

import java.util.regex.Pattern;

/**
 * Makes tenant-supplied text safe to place inside a model prompt.
 *
 * <p>Asset names, notes, descriptions and remediation plans are written by users.
 * Anything that reaches the model from those fields is <em>data</em>. The three
 * things that turn data back into instructions are handled here:
 *
 * <ol>
 *   <li><b>Fence escape</b> — a record whose text contains the delimiter that closes
 *       the untrusted-data block could append its own instructions after it. Every
 *       occurrence of the fence and of the section rules used by the prompt is
 *       neutralised.</li>
 *   <li><b>Role forgery</b> — a line beginning {@code system:} / {@code assistant:}
 *       reads like a new turn to a chat model. Those prefixes are defanged.</li>
 *   <li><b>Budget exhaustion</b> — a single 2MB note would evict the rest of the
 *       context. Every field is truncated to a fixed cap.</li>
 * </ol>
 *
 * <p>Sanitising is not a substitute for the instruction in the system prompt that
 * the block is data; it is the belt to that braces. Neither alone is trusted.
 */
public final class PromptSanitizer {

    /** Delimiter that opens and closes the untrusted-data block. */
    public static final String FENCE = "<<<END_TENANT_DATA>>>";

    /** Longest any single retrieved field may be after sanitising. */
    public static final int MAX_FIELD_CHARS = 400;

    private static final Pattern FENCE_LIKE =
            Pattern.compile("<<<[^>]*>>>|━{2,}|`{3,}", Pattern.CASE_INSENSITIVE);

    private static final Pattern ROLE_PREFIX =
            Pattern.compile("(?im)^\\s*(system|assistant|user|developer)\\s*:");

    /** C0/C1 control characters and the bidi/zero-width formatting tricks. */
    private static final Pattern INVISIBLE =
            Pattern.compile("[\\p{Cntrl}&&[^\n\t]]|[​-‏‪-‮⁦-⁩﻿]");

    private PromptSanitizer() {
    }

    /**
     * Returns {@code value} with fences, forged role prefixes and invisible
     * characters removed, collapsed to single spaces and truncated.
     *
     * @return the sanitised text, or {@code null} when the input was null/blank
     */
    public static String sanitize(String value) {
        return sanitize(value, MAX_FIELD_CHARS);
    }

    /** As {@link #sanitize(String)} with an explicit character cap. */
    public static String sanitize(String value, int maxChars) {
        if (value == null) {
            return null;
        }
        String cleaned = INVISIBLE.matcher(value).replaceAll(" ");
        cleaned = FENCE_LIKE.matcher(cleaned).replaceAll(" ");
        cleaned = ROLE_PREFIX.matcher(cleaned).replaceAll("$1-");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.length() > maxChars) {
            cleaned = cleaned.substring(0, maxChars) + "…";
        }
        return cleaned;
    }

    /**
     * Sanitises a message typed by the caller. The caller's own message is still
     * an instruction — we only strip the characters that would let it forge a
     * turn boundary or smuggle invisible text past a human reviewer.
     */
    public static String sanitizeUserMessage(String message, int maxChars) {
        if (message == null) {
            return "";
        }
        String cleaned = INVISIBLE.matcher(message).replaceAll(" ");
        cleaned = cleaned.replace(FENCE, " ").trim();
        if (cleaned.length() > maxChars) {
            cleaned = cleaned.substring(0, maxChars);
        }
        return cleaned;
    }
}
