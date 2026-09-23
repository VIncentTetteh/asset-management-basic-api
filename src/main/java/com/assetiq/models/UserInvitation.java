package com.assetiq.models;

import com.assetiq.enums.InvitationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * An invitation for someone outside the product to join one organisation with
 * one pre-chosen role.
 *
 * <p>Deliberately not a {@link User}: until the invitee proves they control the
 * mailbox there is no person, so there is no directory entry, no seat consumed,
 * and nothing that can be assigned an asset. The account is created at
 * acceptance, in the organisation named here, with the role named here — the
 * invitee supplies their name and password and nothing else.
 *
 * <p>{@link #tokenHash} is a SHA-256 hex digest. The token itself exists only in
 * the email; it is never stored, never logged, and never returned by the API.
 */
@Entity
@Table(name = "user_invitation")
@Getter
@Setter
public class UserInvitation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    /** Stored lower-cased: an invitation is to a mailbox, and mailboxes are matched case-insensitively. */
    @Column(nullable = false, length = 255)
    private String email;

    /**
     * The role the invitee receives on acceptance. Resolved and
     * privilege-checked when the invitation is issued, and re-checked at
     * acceptance in case it was deleted in between.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "job_title", length = 150)
    private String jobTitle;

    /** Optional line from the inviter, shown on the acceptance screen. */
    @Column(length = 500)
    private String note;

    /** SHA-256 hex of the emailed token — never the token itself. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus status = InvitationStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_user_id")
    private User invitedBy;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_user_id")
    private User acceptedUser;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by", length = 255)
    private String revokedBy;

    @Column(name = "last_sent_at")
    private Instant lastSentAt;

    /** How many times a link has been emailed, counting the first send. */
    @Column(name = "send_count", nullable = false)
    private int sendCount = 1;

    /**
     * Whether the most recent send actually handed a message to the mail
     * transport. False in an environment with email switched off, so the API can
     * tell an admin "copy this link to them yourself" instead of claiming a mail
     * is on its way that nobody will ever receive.
     */
    @Column(name = "email_delivered", nullable = false)
    private boolean emailDelivered = false;

    /** True when this invitation is still redeemable right now. */
    public boolean isRedeemable(Instant now) {
        return status == InvitationStatus.PENDING
                && getDeletedAt() == null
                && expiresAt != null
                && expiresAt.isAfter(now);
    }

    /**
     * The status to report: the stored one, unless it is a PENDING row the clock
     * has overtaken.
     */
    public InvitationStatus effectiveStatus(Instant now) {
        if (status == InvitationStatus.PENDING && expiresAt != null && !expiresAt.isAfter(now)) {
            return InvitationStatus.EXPIRED;
        }
        return status;
    }
}
