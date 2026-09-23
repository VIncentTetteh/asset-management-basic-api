package com.assetiq.dto.invitation;

import java.time.Instant;
import java.util.List;

/**
 * What an unauthenticated invitee is shown before they accept.
 *
 * <p>Scoped to what the token already implies: the company that invited them,
 * the address it was sent to, and what the role will let them do. Nothing about
 * the organisation's other members, and nothing about whether this address
 * exists anywhere else on the platform.
 *
 * @param valid          false when the token is unknown, spent, revoked or expired
 * @param reason         why, when {@code valid} is false — {@code EXPIRED}, {@code REVOKED},
 *                       {@code ACCEPTED} or {@code UNKNOWN}
 * @param permissions    a plain-language list of what the role allows
 */
public record InvitationPreviewDto(boolean valid, String reason,
                                   String organisationName, String email,
                                   String roleName, String roleDescription,
                                   String firstName, String lastName,
                                   String invitedByName, String note,
                                   Instant expiresAt,
                                   List<PermissionDescriptionDto> permissions) {

    public static InvitationPreviewDto invalid(String reason) {
        return new InvitationPreviewDto(false, reason, null, null, null, null,
                null, null, null, null, null, List.of());
    }
}
