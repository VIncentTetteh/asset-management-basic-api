package com.assetiq.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "refresh_session")
public class RefreshSession extends BaseEntity {

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_token_hash", length = 64)
    private String replacedByTokenHash;

    /**
     * When this token was spent by a rotation, as distinct from {@code revokedAt},
     * which is also set by logout, password change and reuse detection.
     */
    @Column(name = "consumed_at")
    private Instant consumedAt;

    /**
     * The replacement refresh token, sealed under a key derived from the raw token
     * this row represents. The server stores only the hash of that raw token, so it
     * cannot open the envelope on its own; a caller presenting the consumed token can.
     * Used only inside the replay grace window — see RefreshSessionService.
     */
    @Column(name = "replacement_envelope", columnDefinition = "text")
    private String replacementEnvelope;

    /**
     * The absolute cap for this whole session family, carried forward unchanged by
     * every rotation. Rotation extends {@code expiresAt} (the idle timeout); it never
     * extends this.
     */
    @Column(name = "family_expires_at")
    private Instant familyExpiresAt;
}
