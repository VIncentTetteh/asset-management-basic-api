package com.assetiq.assets;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * What an asset QR code encodes, and how a scanned value is read back.
 *
 * <p>Labels encode an HTTPS link to the web app's scan page. The first labels encoded
 * the bare text {@code asset:<uuid>}: a phone camera finds nothing to open in that and
 * iOS reports "No usable data found", so scanning a label showed nothing. The link
 * opens the asset summary (after sign-in). Every earlier format is still accepted so
 * labels already printed keep working in the mobile scanner.
 */
public final class AssetQrCodes {

    static final String SCAN_PATH = "/scan";
    static final String ASSET_PARAM = "a";
    private static final String LEGACY_PREFIX = "asset:";

    private AssetQrCodes() {
    }

    /** The link a label encodes: {@code <baseUrl>/scan?a=<uuid>}. */
    public static String link(String baseUrl, UUID assetId) {
        String base = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        return base + SCAN_PATH + "?" + ASSET_PARAM + "=" + assetId;
    }

    /**
     * Reads the asset id out of a scanned value: a scan link, the legacy
     * {@code asset:<uuid>} text, or a bare UUID. Anything else is empty.
     */
    public static Optional<UUID> parse(String scanned) {
        if (scanned == null || scanned.isBlank()) {
            return Optional.empty();
        }
        String value = scanned.trim();
        if (value.toLowerCase(Locale.ROOT).startsWith(LEGACY_PREFIX)) {
            return uuid(value.substring(LEGACY_PREFIX.length()));
        }
        if (value.contains("://")) {
            return fromLink(value);
        }
        return uuid(value);
    }

    private static Optional<UUID> fromLink(String link) {
        try {
            String query = URI.create(link).getRawQuery();
            if (query == null) {
                return Optional.empty();
            }
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0 && ASSET_PARAM.equals(pair.substring(0, eq))) {
                    return uuid(pair.substring(eq + 1));
                }
            }
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Optional<UUID> uuid(String value) {
        try {
            return Optional.of(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
