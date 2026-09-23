package com.assetiq.license.offline;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the self-hosted offline licence mode.
 *
 * <p>Property prefix {@code app.license.offline}. The mode is <strong>off by
 * default</strong>: with {@code enabled=false} no bean in this package is created
 * and entitlement resolution is byte-for-byte what the hosted deployment has
 * always done.</p>
 *
 * <table border="1">
 *   <caption>Properties</caption>
 *   <tr><th>Property</th><th>Env var</th><th>Default</th></tr>
 *   <tr><td>app.license.offline.enabled</td><td>APP_LICENSE_OFFLINE_ENABLED</td><td>false</td></tr>
 *   <tr><td>app.license.offline.key</td><td>APP_LICENSE_OFFLINE_KEY</td><td>(empty)</td></tr>
 *   <tr><td>app.license.offline.public-key</td><td>APP_LICENSE_OFFLINE_PUBLIC_KEY</td><td>(empty — uses the key baked into the image)</td></tr>
 * </table>
 *
 * <p>This mode never reaches the network. There is no licence server, no
 * call-home and no telemetry: the key is a self-contained signed document and
 * verification is a local signature check.</p>
 */
@Component
@ConfigurationProperties(prefix = "app.license.offline")
public class OfflineLicenseProperties {

    /**
     * Master switch. Leave {@code false} for the vendor-hosted deployment, where
     * entitlements come from the Paystack subscription as they always have.
     */
    private boolean enabled = false;

    /** The signed licence key issued to this installation. Blank means unlicensed. */
    private String key = "";

    /**
     * Optional PEM-encoded RSA public key that overrides the one baked into the
     * image at {@code classpath:/license/offline-public.pem}.
     *
     * <p>This is a <em>public</em> key: it is not a secret and putting it in a
     * config map or an env var is fine. It exists so an operator running their
     * own licence issuance, and the test suite, can supply a different trust
     * anchor without rebuilding the image.</p>
     */
    private String publicKey = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }
}
