package com.assetiq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Holds license-related configuration properties.
 *
 * <p>These properties are always <em>read</em> by Spring (so the app starts
 * cleanly regardless of mode), but they are only <em>acted upon</em> by the
 * {@code LicenseService} and {@code LicenseGuardFilter} which are themselves
 * annotated with {@link ConditionalOnAppMode}({@link AppMode#STANDALONE}) and
 * therefore never instantiated in cloud mode.</p>
 *
 * <p>Property prefix: {@code app.license}</p>
 *
 * <table border="1">
 *   <tr><th>Property</th><th>Env var</th><th>Default</th></tr>
 *   <tr><td>app.license.key</td><td>ASSETIQ_LICENSE_KEY</td><td>(empty)</td></tr>
 *   <tr><td>app.license.server-url</td><td>LICENSE_SERVER_URL</td><td>https://license.assetiq.io</td></tr>
 * </table>
 */
@Configuration
@ConfigurationProperties(prefix = "app.license")
public class LicenseProperties {

    /**
     * The RSA-signed license key provided by the Customer Portal after purchase.
     * Format: {@code ASIQ-XXXX-XXXX-XXXX-XXXX-XXXX}
     * Required when {@code app.mode=standalone}.
     */
    private String key = "";

    /**
     * Base URL of the vendor-managed License Server.
     * The standalone backend calls this every 24 h to validate the key remotely.
     */
    private String serverUrl = "https://license.assetiq.io";

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    /** Convenience: {@code true} when a non-blank key is configured. */
    public boolean hasKey() {
        return key != null && !key.isBlank();
    }
}
