package com.assetiq.logging;

import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.nio.charset.StandardCharsets;

/**
 * Plain-text counterpart to {@link RedactingJsonEncoder}, for the console and file
 * appenders used outside the production profile.
 *
 * <p>Dev and test logs are the ones most likely to contain a real token pasted into a
 * request during debugging, and they are routinely copied into tickets and chat. Applying
 * the same {@link LogRedactor} rules here means the redaction guarantee does not depend on
 * which profile happens to be active.
 */
public class RedactingPatternLayoutEncoder extends PatternLayoutEncoder {

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
