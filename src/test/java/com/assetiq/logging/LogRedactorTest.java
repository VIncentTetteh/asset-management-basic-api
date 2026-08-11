package com.assetiq.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The redactor has two jobs and it is easy to do one at the cost of the other: strip
 * secrets, and keep everything else — including the line itself. Its predecessor failed
 * both, deleting whole log events on a keyword match while never inspecting the arguments
 * that actually carried the secret. These tests pin both directions.
 */
@DisplayName("LogRedactor")
class LogRedactorTest {

    @Nested
    @DisplayName("removes secrets")
    class RemovesSecrets {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.c2lnbmF0dXJl",
                "issued token=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.c2lnbmF0dXJl for user",
                "{\"idToken\":\"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.c2lnbmF0dXJl\"}",
        })
        @DisplayName("JWTs in any position")
        void redactsJwts(String line) {
            assertThat(LogRedactor.redact(line))
                    .doesNotContain("eyJhbGciOiJIUzI1NiJ9")
                    .contains(LogRedactor.REDACTED);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "PAYSTACK_SECRET_KEY=sk_live_9f8a7b6c5d4e3f2a1b0c",
                "using key sk_test_9f8a7b6c5d4e3f2a1b0c for checkout",
                "GROQ_API_KEY=gsk_dg2RabcdefghijklmnopQ",
                "anthropic sk-ant-api03-abcdefghijklmnop",
                "aws AKIAIOSFODNN7EXAMPLE",
        })
        @DisplayName("vendor keys recognisable by prefix")
        void redactsVendorKeys(String line) {
            String redacted = LogRedactor.redact(line);
            assertThat(redacted).contains(LogRedactor.REDACTED);
            assertThat(redacted).doesNotContain("9f8a7b6c5d4e3f2a1b0c", "dg2Rabcdefghijklmnop",
                    "api03-abcdefghijklmnop", "IOSFODNN7EXAMPLE");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "login attempt password=hunter2 for admin@example.com",
                "{\"password\":\"hunter2\"}",
                "client_secret: hunter2",
                "mfa_secret=hunter2",
                "totp=hunter2",
                "api_key=hunter2",
        })
        @DisplayName("values assigned to sensitive field names")
        void redactsSensitiveAssignments(String line) {
            assertThat(LogRedactor.redact(line))
                    .doesNotContain("hunter2")
                    .contains(LogRedactor.REDACTED);
        }

        @Test
        @DisplayName("the argument of a parameterised log call, which the old filter never saw")
        void redactsRenderedArguments() {
            // The predecessor inspected the unformatted template "Reset issued for {}",
            // which contains no keyword, and so passed this straight through.
            String rendered = "Reset issued for user@example.com token=abc123def456ghi789";
            assertThat(LogRedactor.redact(rendered)).doesNotContain("abc123def456ghi789");
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
                String redacted = LogRedactor.redact(line);
                assertThat(redacted)
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
        void redactedJsonRemainsParseable() throws Exception {
            // The whole point of moving to a real JSON encoder is machine-readable output;
            // redaction must not undo that by eating a quote or a brace.
            String json = """
                    {"timestamp":"2026-08-11T10:00:00Z","level":"INFO","message":\
                    "login ok password=hunter2 bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhIn0.c2ln",\
                    "mdc":{"requestId":"abc-123"}}""";

            String redacted = LogRedactor.redact(json);

            assertThat(redacted).doesNotContain("hunter2", "eyJhbGciOiJIUzI1NiJ9");
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
