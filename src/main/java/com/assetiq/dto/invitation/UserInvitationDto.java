package com.assetiq.dto.invitation;

import com.assetiq.enums.InvitationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * An invitation as an administrator sees it.
 *
 * <p>No token and no hash: neither is ever returned. {@code acceptUrl} is
 * present only on the response to issuing or resending one, and only when email
 * is switched off — see {@link InvitationIssuedDto}.
 *
 * @param status          the reported status, with expiry already applied
 * @param emailDelivered  whether the last send actually reached the mail transport
 */
public record UserInvitationDto(UUID id, String email, InvitationStatus status,
                                UUID roleId, String roleName,
                                UUID departmentId, String departmentName,
                                String firstName, String lastName, String jobTitle, String note,
                                Instant expiresAt, Instant createdAt, Instant lastSentAt,
                                int sendCount, boolean emailDelivered,
                                String invitedByName,
                                Instant acceptedAt, UUID acceptedUserId,
                                Instant revokedAt) {
}
