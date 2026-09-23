package com.assetiq.services;

import com.assetiq.dto.PagedResponseDto;
import com.assetiq.dto.invitation.AcceptInvitationRequest;
import com.assetiq.dto.invitation.InvitationAcceptedDto;
import com.assetiq.dto.invitation.InvitationIssuedDto;
import com.assetiq.dto.invitation.InvitationPreviewDto;
import com.assetiq.dto.invitation.InviteUserRequest;
import com.assetiq.dto.invitation.UserInvitationDto;

import java.util.UUID;

/**
 * Inviting a colleague into the caller's own organisation, and letting them join.
 *
 * <p>Every method but {@link #preview} and {@link #accept} is tenant-scoped and
 * reads the organisation from the authenticated context. Those two are reached
 * by someone who has no account yet, so they authenticate with the token alone
 * and derive the organisation from it — never from a header, a body field, or
 * anything else the caller chooses.
 */
public interface UserInvitationService {

    /**
     * Issues (or re-issues) an invitation and emails the link.
     *
     * <p>Inviting an address that already has a live invitation in this tenant
     * re-issues that one — a new token, a new expiry, the role the caller just
     * chose — rather than creating a second row, so the list never shows two
     * invitations for the same person and the older link stops working.
     *
     * @throws IllegalStateException when the address already belongs to a member here
     * @throws org.springframework.security.access.AccessDeniedException when the
     *         role grants more than the caller holds, or no seat is free
     * @throws com.assetiq.exceptions.TooManyRequestsException when the tenant's hourly send allowance is spent
     */
    InvitationIssuedDto invite(InviteUserRequest request);

    /** Issues a fresh token for a pending invitation and emails it again. */
    InvitationIssuedDto resend(UUID invitationId);

    /** Withdraws a pending invitation; its link stops working immediately. */
    UserInvitationDto revoke(UUID invitationId);

    /**
     * The tenant's invitations, newest first.
     *
     * @param status optional filter: PENDING, ACCEPTED, REVOKED or EXPIRED.
     *               EXPIRED is a clock comparison, not a stored value.
     */
    PagedResponseDto<UserInvitationDto> list(String status, int limit, int offset);

    /**
     * What the token is worth, for the acceptance screen. Never throws for a bad
     * token: it answers {@code valid = false} with a reason, because an invitee
     * holding a stale link needs to be told what happened, and an attacker
     * probing tokens learns the same nothing either way.
     */
    InvitationPreviewDto preview(String rawToken);

    /**
     * Redeems a token: creates the account in the inviting organisation with the
     * invited role, and spends the token.
     *
     * @throws IllegalArgumentException when the token is unknown, spent, revoked or expired
     * @throws IllegalStateException when the address already belongs to a member there
     * @throws org.springframework.security.access.AccessDeniedException when no seat is free
     */
    InvitationAcceptedDto accept(AcceptInvitationRequest request);
}
