package com.assetiq.services;

import com.assetiq.enums.UserStatus;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import com.assetiq.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * The one place a person's identity is erased from a tenant.
 *
 * <p>Two callers need this and they must not drift apart: a user deleting their
 * own account from the app, and a compliance officer fulfilling an ERASURE DSAR.
 * If those two anonymised a user differently, one of them would be wrong, and the
 * organisation's answer to a regulator would depend on which route was taken.
 *
 * <p>What erasure means here: the rows the person authored stay (an asset's
 * history is the organisation's record, not the individual's), but everything
 * that identifies them is overwritten, their credentials are destroyed, and their
 * account is soft-deleted so it cannot be signed into or restored by a password
 * reset. The email is replaced rather than blanked because it is NOT NULL and
 * unique per organisation; the replacement is deliberately in the reserved
 * {@code .invalid} TLD (RFC 2606), so it can never route anywhere.
 */
@Service
public class UserErasureService {

    private static final Logger log = LoggerFactory.getLogger(UserErasureService.class);

    /** RFC 2606 reserves .invalid: an address here is guaranteed undeliverable. */
    static final String ERASED_EMAIL_DOMAIN = "@erased.invalid";
    static final String ERASED_NAME = "Erased";

    private final UserRepository userRepository;
    private final SessionRevocationService sessionRevocationService;
    private final PasswordEncoder passwordEncoder;

    public UserErasureService(UserRepository userRepository,
                              SessionRevocationService sessionRevocationService,
                              PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.sessionRevocationService = sessionRevocationService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Anonymises and soft-deletes one account, and revokes every session it holds.
     *
     * <p>Idempotent: erasing an already-erased account changes nothing further.
     *
     * @param reason why the erasure happened — recorded in the log, never in a column
     *               that could reintroduce the identity being removed.
     * @return the erased user.
     */
    @Transactional
    public User erase(User user, String reason) {
        if (user.getDeletedAt() != null && user.getEmail() != null
                && user.getEmail().endsWith(ERASED_EMAIL_DOMAIN)) {
            return user;
        }
        UUID userId = user.getId();
        Organisation org = user.getOrganisation();

        user.setEmail(userId + ERASED_EMAIL_DOMAIN);
        user.setFirstName(ERASED_NAME);
        user.setLastName(ERASED_NAME);
        user.setPhone(null);
        user.setJobTitle(null);

        // Credentials are destroyed, not merely disabled: a random hash nobody
        // holds the input for means no reset flow and no stored-hash reuse.
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiry(null);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationTokenExpiry(null);
        user.setEmailVerifiedAt(null);
        user.setLastLoginAt(null);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);

        user.setStatus(UserStatus.TERMINATED);
        user.setDeletedAt(Instant.now());

        // Bumps sessionVersion, which every issued access token carries: the
        // account's tokens stop validating on the next request, not at expiry.
        sessionRevocationService.revokeAll(user);
        User erased = userRepository.save(user);

        log.warn("[ERASURE] User {} of organisation {} erased (reason: {})",
                userId, org == null ? "none" : org.getId(), reason == null ? "not given" : reason);
        return erased;
    }
}
