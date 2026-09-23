package com.assetiq.dto.invitation;

import java.util.UUID;

/**
 * The answer to redeeming an invitation. No session is issued: the invitee signs
 * in with the password they just chose, through the ordinary login flow, so
 * there is one place where sessions, MFA and lockout are decided.
 *
 * @param organisationId  the organisation they are now a member of — the web app
 *                        needs it for the login call when an address exists in
 *                        more than one tenant
 */
public record InvitationAcceptedDto(UUID userId, String email, UUID organisationId,
                                    String organisationName, String roleName, String message) {
}
