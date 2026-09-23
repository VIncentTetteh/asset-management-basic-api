package com.assetiq.dto.invitation;

/**
 * The answer to issuing or resending an invitation.
 *
 * <p>{@code emailSent} reports what actually happened, not what was intended: in
 * an environment with email switched off it is false, and {@code acceptUrl}
 * carries the link so the administrator can pass it on themselves. When email is
 * on, {@code acceptUrl} is null — the token belongs in the mailbox it was sent
 * to and nowhere else.
 *
 * @param invitation the invitation record
 * @param emailSent  whether a message was handed to the mail transport
 * @param acceptUrl  the one-time link, present only when {@code emailSent} is false
 * @param message    a sentence the UI can show as-is
 */
public record InvitationIssuedDto(UserInvitationDto invitation, boolean emailSent,
                                  String acceptUrl, String message) {
}
