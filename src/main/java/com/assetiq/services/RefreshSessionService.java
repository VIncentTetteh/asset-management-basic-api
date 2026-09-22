package com.assetiq.services;

import com.assetiq.enums.OrganisationStatus;
import com.assetiq.enums.UserStatus;
import com.assetiq.models.RefreshSession;
import com.assetiq.models.User;
import com.assetiq.repositories.RefreshSessionRepository;
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
import java.util.UUID;

/** Rotating, one-time-use refresh sessions with family-level reuse detection. */
@Service
@Transactional
public class RefreshSessionService {

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
    private final Duration lifetime;

    public RefreshSessionService(
            RefreshSessionRepository repository,
            @Value("${app.auth.refresh-token-lifetime:PT12H}") Duration lifetime) {
        this.repository = repository;
        this.lifetime = lifetime;
    }

    public IssuedRefreshToken issue(User user) {
        return create(user, UUID.randomUUID());
    }

    public RotatedRefreshToken rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AccessDeniedException("Refresh token is required");
        }
        RefreshSession current = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new AccessDeniedException("Invalid refresh session"));

        if (current.getRevokedAt() != null) {
            revokeFamily(current.getFamilyId());
            throw new AccessDeniedException("Refresh token reuse detected; session family revoked");
        }
        if (current.getExpiresAt().isBefore(Instant.now())) {
            current.setRevokedAt(Instant.now());
            repository.save(current);
            throw new AccessDeniedException("Refresh session expired");
        }

        User user = current.getUser();
        if (user.getDeletedAt() != null || user.getStatus() != UserStatus.ACTIVE || user.isLockedOut()
                || user.getOrganisation() == null || user.getOrganisation().getDeletedAt() != null
                || user.getOrganisation().getStatus() != OrganisationStatus.ACTIVE) {
            revokeFamily(current.getFamilyId());
            throw new AccessDeniedException("Account or organisation is not active");
        }

        IssuedRefreshToken next = create(user, current.getFamilyId());
        current.setRevokedAt(Instant.now());
        current.setReplacedByTokenHash(hash(next.token()));
        repository.save(current);
        return new RotatedRefreshToken(user, next.token(), next.expiresAt(),
                user.getRole() == null ? null : user.getRole().getName(),
                user.getOrganisation().getId(),
                user.getDepartment() == null ? null : user.getDepartment().getId());
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

    private IssuedRefreshToken create(User user, UUID familyId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshSession session = new RefreshSession();
        session.setTokenHash(hash(raw));
        session.setFamilyId(familyId);
        session.setUser(user);
        session.setExpiresAt(Instant.now().plus(lifetime));
        repository.save(session);
        return new IssuedRefreshToken(raw, session.getExpiresAt());
    }

    private void revokeFamily(UUID familyId) {
        Instant now = Instant.now();
        for (RefreshSession session : repository.findByFamilyIdAndRevokedAtIsNull(familyId)) {
            session.setRevokedAt(now);
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
