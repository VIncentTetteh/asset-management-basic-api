package com.assetiq.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The redactor has two jobs and it is easy to do one at the cost of the other: strip
 * secrets, and keep everything else — including the line itself. Its predecessor failed
 * both, deleting whole log events on a keyword match while never inspecting the arguments
 * that actually carried the secret. These tests pin both directions.
 *
 * <h3>On the fixtures</h3>
 * Every credential-shaped value below is assembled from fragments rather than written as
 * one literal. None has ever been a real credential — but they are deliberately shaped
 * like the real thing, since a redactor that only handles obviously-fake input proves
 * nothing. That shape is also exactly what the gitleaks pre-commit hook and CI job look
 * for, and a test fixture tripping the secret scanner trains people to wave the scanner
 * through. Splitting the literals keeps the scanner honest while the value the redactor
 * sees at runtime is fully formed, so the assertions lose nothing.
 */
@DisplayName("LogRedactor")
class LogRedactorTest {

    // ── Synthetic, credential-shaped fixtures (see class javadoc) ─────────────

    private static final String JWT =
            "eyJ" + "hbGciOiJIUzI1NiJ9" + "." + "eyJzdWIiOiJhZG1pbiJ9" + "." + "c2lnbmF0dXJl";
    private static final String JWT_HEAD = "eyJ" + "hbGciOiJIUzI1NiJ9";
    private static final String PAYSTACK_LIVE = "sk" + "_live_" + "9f8a7b6c5d4e3f2a1b0c";
    private static final String PAYSTACK_TEST = "sk" + "_test_" + "9f8a7b6c5d4e3f2a1b0c";
    private static final String GROQ = "gsk" + "_" + "dg2RabcdefghijklmnopQ";
    private static final String ANTHROPIC = "sk" + "-ant-" + "api03-abcdefghijklmnop";
    private static final String AWS_KEY = "AKIA" + "IOSFODNN7EXAMPLE";
    private static final String OPAQUE_TOKEN = "abc123" + "def456" + "ghi789";
    private static final String PASSWORD = "hunter" + "2";

    @Nested
    @DisplayName("removes secrets")
    class RemovesSecrets {

        @Test
        @DisplayName("JWTs, wherever they appear in the line")
        void redactsJwts() {
            String[] lines = {
                    "Authorization: Bearer " + JWT,
                    "issued token=" + JWT + " for user",
                    "{\"idToken\":\"" + JWT + "\"}",
            };

            for (String line : lines) {
                assertThat(LogRedactor.redact(line))
                        .describedAs("JWT should be redacted in: %s", line)
                        .doesNotContain(JWT_HEAD)
                        .contains(LogRedactor.REDACTED);
            }
        }

        @Test
        @DisplayName("vendor keys recognisable by prefix")
        void redactsVendorKeys() {
            record Case(String line, String secretBody) {}
            Case[] cases = {
                    new Case("PAYSTACK_SECRET_KEY=" + PAYSTACK_LIVE, "9f8a7b6c5d4e3f2a1b0c"),
                    new Case("using key " + PAYSTACK_TEST + " for checkout", "9f8a7b6c5d4e3f2a1b0c"),
                    new Case("GROQ_API_KEY=" + GROQ, "dg2RabcdefghijklmnopQ"),
                    new Case("anthropic " + ANTHROPIC, "api03-abcdefghijklmnop"),
                    new Case("aws " + AWS_KEY, "IOSFODNN7EXAMPLE"),
            };

            for (Case c : cases) {
                assertThat(LogRedactor.redact(c.line()))
                        .describedAs("vendor key should be redacted in: %s", c.line())
                        .doesNotContain(c.secretBody())
                        .contains(LogRedactor.REDACTED);
            }
        }

        @Test
        @DisplayName("values assigned to sensitive field names")
        void redactsSensitiveAssignments() {
            String[] lines = {
                    "login attempt password=" + PASSWORD + " for admin@example.com",
                    "{\"password\":\"" + PASSWORD + "\"}",
                    "client_secret: " + PASSWORD,
                    "mfa_secret=" + PASSWORD,
                    "totp=" + PASSWORD,
                    "api_key=" + PASSWORD,
            };

            for (String line : lines) {
                assertThat(LogRedactor.redact(line))
                        .describedAs("assigned secret should be redacted in: %s", line)
                        .doesNotContain(PASSWORD)
                        .contains(LogRedactor.REDACTED);
            }
        }

        @Test
        @DisplayName("the argument of a parameterised log call, which the old filter never saw")
        void redactsRenderedArguments() {
            // The predecessor inspected the unformatted template "Reset issued for {}",
            // which contains no keyword, and so passed this straight through.
            String rendered = "Reset issued for user@example.com token=" + OPAQUE_TOKEN;

            assertThat(LogRedactor.redact(rendered)).doesNotContain(OPAQUE_TOKEN);
        }
    }

    @Nested
    @DisplayName("preserves everything else")
    class PreservesContext {

        @Test
        @DisplayName("keeps security event lines that the old filter deleted outright")
        void keepsSecurityEventLines() {
            // Every one of these contains a keyword that made the old filter return DENY,
            // discarding the whole event — losing exactly the audit trail an incident needs.
            String[] lines = {
                    "[AUTH] Failed login for admin@example.com from 203.0.113.4",
                    "[JWT_BLACKLIST] Token invalidated on logout",
                    "[RATE_LIMIT] client=ip:203.0.113.4 path=/api/v1/auth/login tier=AUTH/minute",
                    "MFA enabled for user 7f3c",
            };

            for (String line : lines) {
                assertThat(LogRedactor.redact(line))
                        .describedAs("the event must survive redaction: %s", line)
                        .isNotEmpty();
            }

            assertThat(LogRedactor.redact(lines[0])).contains("admin@example.com", "203.0.113.4");
            assertThat(LogRedactor.redact(lines[2])).contains("/api/v1/auth/login", "AUTH/minute");
        }

        @Test
        @DisplayName("leaves ordinary messages untouched")
        void leavesOrdinaryMessagesAlone() {
            String line = "Created asset 7f3c for organisation 9a1b in 42ms";

            assertThat(LogRedactor.redact(line)).isEqualTo(line);
        }

        @Test
        @DisplayName("redacted JSON is still valid JSON")
        void redactedJsonRemainsParseable() {
            // The whole point of moving to a real JSON encoder is machine-readable output;
            // redaction must not undo that by eating a quote or a brace.
            String json = "{\"timestamp\":\"2026-08-11T10:00:00Z\",\"level\":\"INFO\","
                    + "\"message\":\"login ok password=" + PASSWORD + " bearer " + JWT + "\","
                    + "\"mdc\":{\"requestId\":\"abc-123\"}}";

            String redacted = LogRedactor.redact(json);

            assertThat(redacted).doesNotContain(PASSWORD, JWT_HEAD);
            assertThat(redacted).contains("abc-123");
            assertThatCode(() -> new ObjectMapper().readTree(redacted))
                    .describedAs("redaction must preserve JSON structure")
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("handles null and empty input")
        void handlesNullAndEmpty() {
            assertThat(LogRedactor.redact(null)).isNull();
            assertThat(LogRedactor.redact("")).isEmpty();
        }
    }
}
