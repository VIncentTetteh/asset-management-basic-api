package com.assetiq.logging;

import ch.qos.logback.classic.encoder.JsonEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.nio.charset.StandardCharsets;

/**
 * Logback's real {@link JsonEncoder} with {@link LogRedactor} applied to the rendered line.
 *
 * <p>The production profile previously emitted "JSON" from a hand-written
 * {@code PatternLayoutEncoder} pattern that escaped only double quotes. Any log message
 * containing a newline, a backslash, or a control character — a stack trace, most
 * obviously, which is the case that matters most — produced a line that was not valid
 * JSON, so the aggregator dropped or mangled exactly the events worth keeping. This uses
 * Logback's own encoder, which escapes correctly and emits MDC (and therefore
 * {@code requestId}) as structured fields rather than interpolated text.
 *
 * <p>Redaction is applied to the encoded bytes so it covers the message, the arguments,
 * the MDC and the stack trace in one place. Because {@code LogRedactor} only substitutes
 * values and never structural characters, the output remains parseable.
 */
public class RedactingJsonEncoder extends JsonEncoder {

    @Override
    public byte[] encode(ILoggingEvent event) {
        byte[] encoded = super.encode(event);
        if (encoded == null || encoded.length == 0) {
            return encoded;
        }
        String redacted = LogRedactor.redact(new String(encoded, StandardCharsets.UTF_8));
        return redacted.getBytes(StandardCharsets.UTF_8);
    }
}
