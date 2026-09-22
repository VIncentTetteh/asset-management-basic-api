package com.assetiq.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Validates {@link HttpUrl}. */
public class HttpUrlValidator implements ConstraintValidator<HttpUrl, String> {

    /**
     * Browsers drop leading C0 controls and spaces and remove tabs and newlines
     * anywhere before parsing a URL, so "\u0001java\tscript:" still runs as
     * javascript:. The scheme check is made on the value normalised the same way.
     */
    private static final Pattern LEADING_C0_OR_SPACE = Pattern.compile("^[\\u0000-\\u0020]+");
    private static final Pattern TAB_OR_NEWLINE = Pattern.compile("[\\t\\n\\r]");
    /** A URL scheme: letters, digits, + . - then a colon not followed by whitespace. */
    private static final Pattern SCHEME = Pattern.compile("^([A-Za-z][A-Za-z0-9+.\\-]*):(?!\\s)");

    private boolean allowPlainText;
    private boolean httpsOnly;

    @Override
    public void initialize(HttpUrl annotation) {
        this.allowPlainText = annotation.allowPlainText();
        this.httpsOnly = annotation.httpsOnly();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalised = TAB_OR_NEWLINE.matcher(LEADING_C0_OR_SPACE.matcher(value).replaceFirst("")).replaceAll("");
        boolean hasScheme = SCHEME.matcher(normalised).find();
        if (!hasScheme) {
            return allowPlainText;
        }
        String url = value.strip();
        return isAbsoluteHttpUrl(url) && (!httpsOnly || url.regionMatches(true, 0, "https:", 0, 6));
    }

    static boolean isAbsoluteHttpUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme == null) return false;
            String lower = scheme.toLowerCase(Locale.ROOT);
            return (lower.equals("http") || lower.equals("https")) && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
