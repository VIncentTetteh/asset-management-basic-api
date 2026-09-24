package com.assetiq.services;

import com.assetiq.enums.OrganisationStatus;
import com.assetiq.enums.UserStatus;
import com.assetiq.models.RefreshSession;
import com.assetiq.models.User;
import com.assetiq.repositories.RefreshSessionRepository;
import com.assetiq.security.RefreshReplaySeal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Rotating, one-time-use refresh sessions with family-level reuse detection.
 *
 * <h2>Two clocks, not one</h2>
 * A family has an <b>idle timeout</b> ({@code app.auth.refresh-token-lifetime}) that
 * every rotation resets, and an <b>absolute cap</b>
 * ({@code app.auth.refresh-absolute-lifetime}) that no rotation can extend. A device
 * used daily therefore stays signed in until the cap; a device that goes quiet dies on
 * the idle clock. Revocation — logout, password change, MFA change, erasure — still
 * kills the family instantly and is unaffected by either clock.
 *
 * <h2>The replay grace window</h2>
 * Reuse detection is correct but has no tolerance for the ordinary mobile failure: a
 * process killed between the server committing a rotation and the device writing the
 * replacement to disk. The next launch presents a spent token and, without a grace
 * window, the user is signed out of every device with no explanation.
 *
 * <p>So a token consumed within {@code app.auth.refresh-replay-grace} <em>replays</em>
 * its replacement instead of tripping detection — the same token, never a second one,
 * so the family cannot fork. It replays only while the replacement is still the live
 * head of the family: once the family has moved on (the replacement was itself consumed
 * or revoked), or once the window has passed, the presentation is reuse and revokes the
 * family exactly as before. An attacker replaying a token minutes later gets the old
 * behaviour.
 *
 * <p>Concurrency is handled by the row lock: {@code findByTokenHash} takes
 * {@code PESSIMISTIC_WRITE}, so two devices presenting the same token serialise. The
 * first consumes it; the second reads the committed row, finds it consumed, and replays
 * the same replacement. One replacement, one head, no fork.
 */
@Service
@Transactional
public class RefreshSessionService {

    private static final Logger log = LoggerFactory.getLogger(RefreshSessionService.class);

    public record IssuedRefreshToken(String token, Instant expiresAt) {}
    /**
     * The rotated session plus everything the caller needs to mint the access token.
     *
     * <p>Role, organisation and department are captured here, inside the transaction.
     * The caller builds the token after this method returns, when the session is closed
     * (open-in-view is off), so reading those lazy associations there threw
     * LazyInitializationException: every silent refresh failed with a 500 and users were
     * logged out when their access token expired.
     */
    public record RotatedRefreshToken(User user, String token, Instant expiresAt,
                                      String roleName, UUID organisationId, UUID departmentId) {}

    private static final SecureRandom RANDOM = new SecureRandom();
    private final RefreshSessionRepository repository;
    private final Duration idleTimeout;
    private final Duration absoluteLifetime;
    private final Duration replayGrace;

    public RefreshSessionService(
            RefreshSessionRepository repository,
            @Value("${app.auth.refresh-token-lifetime:P30D}") Duration idleTimeout,
            @Value("${app.auth.refresh-absolute-lifetime:P90D}") Duration absoluteLifetime,
            @Value("${app.auth.refresh-replay-grace:PT30S}") Duration replayGrace) {
        this.repository = repository;
        this.idleTimeout = idleTimeout;
        // A cap shorter than the idle window is not corrected upwards — that would
        // quietly lengthen sessions. create() clamps each token to whichever clock
        // falls first, so a short cap simply becomes the only one that fires.
        this.absoluteLifetime = absoluteLifetime;
        this.replayGrace = replayGrace.isNegative() ? Duration.ZERO : replayGrace;
    }

    public IssuedRefreshToken issue(User user) {
        return create(user, UUID.randomUUID(), Instant.now().plus(absoluteLifetime));
    }

    /**
     * Rejections must still commit what they wrote.
     *
     * <p>Every rejection here is an AccessDeniedException, and the default rollback
     * rule for a RuntimeException rolled the transaction back — taking the family
     * revocation with it. Reuse detection therefore <em>revoked nothing</em>: it
     * returned 401 and left every stolen token in the family live. The expiry path
     * lost its write for the same reason. Rejections are a normal outcome of this
     * method, not a failure, so they do not roll back.
     */
    @Transactional(noRollbackFor = AccessDeniedException.class)
    public RotatedRefreshToken rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AccessDeniedException("Refresh token is required");
        }
        RefreshSession current = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new AccessDeniedException("Invalid refresh session"));
        Instant now = Instant.now();

        if (current.getRevokedAt() != null) {
            RotatedRefreshToken replay = replayWithinGrace(current, rawToken, now);
            if (replay != null) return replay;
            revokeFamily(current.getFamilyId());
            log.warn("[AUTH_REFRESH] Reuse detected; revoked session family {} for user {}",
                    current.getFamilyId(), userIdOf(current));
            throw new AccessDeniedException("Refresh token reuse detected; session family revoked");
        }
        if (current.getExpiresAt().isBefore(now)) {
            current.setRevokedAt(now);
            repository.save(current);
            throw new AccessDeniedException("Refresh session expired");
        }
        if (current.getFamilyExpiresAt() != null && current.getFamilyExpiresAt().isBefore(now)) {
            // The absolute cap: re-authentication is due regardless of how active the
            // device has been. The whole family goes, not just this token.
            revokeFamily(current.getFamilyId());
            current.setRevokedAt(now);
            repository.save(current);
            throw new AccessDeniedException("Refresh session expired");
        }

        User user = current.getUser();
        requireUsableAccount(user, current.getFamilyId());

        Instant familyExpiresAt = current.getFamilyExpiresAt() != null
                ? current.getFamilyExpiresAt()
                : now.plus(absoluteLifetime);
        IssuedRefreshToken next = create(user, current.getFamilyId(), familyExpiresAt);
        current.setRevokedAt(now);
        current.setConsumedAt(now);
        current.setReplacedByTokenHash(hash(next.token()));
        // Sealed under the token the caller just presented; see RefreshReplaySeal.
        current.setReplacementEnvelope(RefreshReplaySeal.seal(rawToken, next.token()));
        repository.save(current);
        return rotated(user, next.token(), next.expiresAt());
    }

    /**
     * Replays the replacement of a token consumed moments ago, or returns null when this
     * presentation is genuine reuse and must revoke the family.
     */
    private RotatedRefreshToken replayWithinGrace(RefreshSession consumed, String rawToken, Instant now) {
        if (replayGrace.isZero()) return null;
        Instant consumedAt = consumed.getConsumedAt();
        if (consumedAt == null || consumed.getReplacedByTokenHash() == null) {
            return null; // revoked rather than consumed: logout, password change, reuse
        }
        if (consumedAt.plus(replayGrace).isBefore(now)) {
            return null; // outside the window — an attacker replaying later still trips
        }

        Optional<RefreshSession> head = repository.findByTokenHash(consumed.getReplacedByTokenHash());
        if (head.isEmpty()) return null;
        RefreshSession replacement = head.get();
        boolean stillHead = replacement.getRevokedAt() == null
                && replacement.getExpiresAt().isAfter(now)
                && (replacement.getFamilyExpiresAt() == null || replacement.getFamilyExpiresAt().isAfter(now));
        if (!stillHead) {
            // The family moved on, or was revoked out from under us. Reuse.
            return null;
        }

        String raw = RefreshReplaySeal.unseal(rawToken, consumed.getReplacementEnvelope());
        if (raw == null || !hash(raw).equals(replacement.getTokenHash())) {
            return null; // fail closed: an envelope we cannot trust is not a replay
        }

        User user = replacement.getUser();
        requireUsableAccount(user, replacement.getFamilyId());

        // Operator-actionable: a burst of these is a client that is losing its writes,
        // a sustained stream is worth investigating. No token material, ever.
        log.warn("[AUTH_REFRESH] Replayed replacement for a token consumed {} ms ago "
                        + "(family {}, user {}); within the {} grace window, not reuse",
                Duration.between(consumedAt, now).toMillis(), consumed.getFamilyId(),
                userIdOf(consumed), replayGrace);
        return rotated(user, raw, replacement.getExpiresAt());
    }

    private RotatedRefreshToken rotated(User user, String token, Instant expiresAt) {
        return new RotatedRefreshToken(user, token, expiresAt,
                user.getRole() == null ? null : user.getRole().getName(),
                user.getOrganisation().getId(),
                user.getDepartment() == null ? null : user.getDepartment().getId());
    }

    private void requireUsableAccount(User user, UUID familyId) {
        if (user.getDeletedAt() != null || user.getStatus() != UserStatus.ACTIVE || user.isLockedOut()
                || user.getOrganisation() == null || user.getOrganisation().getDeletedAt() != null
                || user.getOrganisation().getStatus() != OrganisationStatus.ACTIVE) {
            revokeFamily(familyId);
            throw new AccessDeniedException("Account or organisation is not active");
        }
    }

    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        repository.findByTokenHash(hash(rawToken)).ifPresent(session -> revokeFamily(session.getFamilyId()));
    }

    public void revokeAll(User user) {
        if (user == null || user.getId() == null) return;
        Instant now = Instant.now();
        List<RefreshSession> active = repository.findByUserIdAndRevokedAtIsNull(user.getId());
        active.forEach(session -> session.setRevokedAt(now));
        if (!active.isEmpty()) repository.saveAll(active);
    }

    private IssuedRefreshToken create(User user, UUID familyId, Instant familyExpiresAt) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant idleExpiry = Instant.now().plus(idleTimeout);
        RefreshSession session = new RefreshSession();
        session.setTokenHash(hash(raw));
        session.setFamilyId(familyId);
        session.setUser(user);
        session.setFamilyExpiresAt(familyExpiresAt);
        // Neither clock may be outlived: the token dies at whichever comes first.
        session.setExpiresAt(idleExpiry.isAfter(familyExpiresAt) ? familyExpiresAt : idleExpiry);
        repository.save(session);
        return new IssuedRefreshToken(raw, session.getExpiresAt());
    }

    private void revokeFamily(UUID familyId) {
        Instant now = Instant.now();
        for (RefreshSession session : repository.findByFamilyIdAndRevokedAtIsNull(familyId)) {
            session.setRevokedAt(now);
        }
    }

    private static UUID userIdOf(RefreshSession session) {
        try {
            return session.getUser() == null ? null : session.getUser().getId();
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
