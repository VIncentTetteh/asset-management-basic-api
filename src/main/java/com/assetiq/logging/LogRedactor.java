package com.assetiq.logging;

import java.util.regex.Pattern;

/**
 * Redacts secret-looking values from a log line.
 *
 * <h3>What this replaces</h3>
 * The previous {@code SensitiveDataFilter} was a Logback {@code Filter} that returned
 * {@code DENY} for the whole event when the message contained any of {@code password},
 * {@code token}, {@code authorization}, {@code bearer}, {@code secret}, {@code otp},
 * {@code mfa}… That was wrong in both directions:
 *
 * <ul>
 *   <li><b>It deleted evidence.</b> Dropping the entire line meant every
 *       {@code [AUTH]} and {@code [JWT_BLACKLIST]} message disappeared — exactly the
 *       security events you most want during an incident.</li>
 *   <li><b>It did not actually protect anything.</b> It inspected
 *       {@code event.getMessage()}, the <em>unformatted</em> template. For the usual
 *       {@code log.info("...: {}", value)} shape the secret lives in the arguments,
 *       which were never examined. A line reading "Reset link issued: {}" passed the
 *       filter with the token intact.</li>
 * </ul>
 *
 * <h3>Approach</h3>
 * Redaction happens on the fully rendered line, after the encoder has substituted
 * arguments and serialised MDC. That is the only point where the actual bytes about to
 * be written are visible, and it means the same rules apply to plain-text and JSON
 * output alike rather than depending on a pattern converter that JSON encoding would
 * bypass. The line is kept; only the value is replaced.
 *
 * <p>This is defence in depth, not a licence to log secrets. The primary rule still
 * stands: do not pass credentials to a logger.
 */
public final class LogRedactor {

    public static final String REDACTED = "***REDACTED***";

    /**
     * JWTs — three base64url segments. These appear in Authorization headers, password
     * reset links, MFA challenge tokens, and SSO exchange payloads. Matched first
     * because a JWT can otherwise be partially caught by the key/value rules below.
     */
    private static final Pattern JWT = Pattern.compile(
            "eyJ[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{4,}");

    /** "Bearer <token>" in any casing, as it appears in header dumps. */
    private static final Pattern BEARER = Pattern.compile(
            "(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]{8,}");

    /**
     * Vendor keys that are recognisable by prefix on their own: Paystack secret/public,
     * Groq, OpenAI, Anthropic, GitHub, AWS access key ids, Slack.
     */
    private static final Pattern VENDOR_KEY = Pattern.compile(
            "\\b(sk_live_|sk_test_|pk_live_|pk_test_|gsk_|sk-ant-|sk-|ghp_|gho_|AKIA|xox[baprs]-)"
            + "[A-Za-z0-9._-]{6,}");

    /**
     * key=value and "key": "value" for sensitive field names, covering both plain-text
     * message bodies and JSON-serialised fields. The value stops at the first delimiter
     * so surrounding structure survives — important for JSON, which must stay parseable.
     */
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)([\"']?\\b(?:password|passwd|pwd|secret|client_secret|api[_-]?key|apikey|"
            + "access[_-]?token|refresh[_-]?token|id[_-]?token|token|authorization|auth|"
            + "otp|totp|mfa[_-]?secret|signing[_-]?secret|private[_-]?key|ssn|"
            + "credit[_-]?card|ccn|cvv)[\"']?\\s*[:=]\\s*[\"']?)"
            + "([^\"'\\s,;}&]+)");

    private LogRedactor() {
    }

    /**
     * Returns {@code line} with any recognised secret value replaced. Structure —
     * quotes, braces, separators — is preserved so a redacted JSON line is still valid
     * JSON and a redacted text line is still readable.
     */
    public static String redact(String line) {
        if (line == null || line.isEmpty()) {
            return line;
        }

        String result = JWT.matcher(line).replaceAll(REDACTED);
        result = BEARER.matcher(result).replaceAll("$1" + REDACTED);
        result = VENDOR_KEY.matcher(result).replaceAll(REDACTED);
        result = SENSITIVE_ASSIGNMENT.matcher(result).replaceAll("$1" + REDACTED);
        return result;
    }
}
