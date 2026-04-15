package com.assetiq.license;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Single-row DB table that persists:
 * <ul>
 *   <li>The active license key token (so it survives restarts)</li>
 *   <li>The timestamp of the last successful remote validation (for grace period tracking)</li>
 *   <li>The cached remote validation response JSON (reduces cold-start API calls)</li>
 * </ul>
 *
 * Only used in {@code APP_MODE=standalone}. The row is created on first
 * activation via {@code POST /api/v1/license/activate}.
 */
@Entity
@Table(name = "license_settings")
public class LicenseSettings {

    /**
     * Always 1 — only one row is ever written.
     * Not {@code final} so Hibernate can proxy this entity without errors.
     */
    @Id
    private Long id = 1L;

    /** The raw RSA-signed JWT string entered by the admin. */
    @Column(name = "key_token", columnDefinition = "TEXT")
    private String keyToken;

    /**
     * Timestamp of the last successful call to the License Server's
     * {@code POST /v1/validate} endpoint. Used to determine whether the
     * grace period clock has started.
     */
    @Column(name = "last_remote_validation_at")
    private Instant lastRemoteValidationAt;

    /** Last raw JSON response body from the License Server (for debugging). */
    @Column(name = "remote_validation_cache", columnDefinition = "TEXT")
    private String remoteValidationCache;

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @PrePersist
    @PreUpdate
    void touch() { this.updatedAt = Instant.now(); }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getKeyToken() { return keyToken; }
    public void setKeyToken(String keyToken) {
        this.keyToken = keyToken;
        this.updatedAt = Instant.now();
    }

    public Instant getLastRemoteValidationAt() { return lastRemoteValidationAt; }
    public void setLastRemoteValidationAt(Instant ts) {
        this.lastRemoteValidationAt = ts;
        this.updatedAt = Instant.now();
    }

    public String getRemoteValidationCache() { return remoteValidationCache; }
    public void setRemoteValidationCache(String cache) {
        this.remoteValidationCache = cache;
        this.updatedAt = Instant.now();
    }

    public Instant getUpdatedAt() { return updatedAt; }
}
