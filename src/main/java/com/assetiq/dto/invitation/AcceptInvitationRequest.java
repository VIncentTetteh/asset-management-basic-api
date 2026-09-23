package com.assetiq.dto.invitation;

import com.assetiq.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * What the invitee supplies to redeem a link.
 *
 * <p>There is no organisation, no role and no email field: all three come from
 * the invitation the token names. An invitee can choose their name and their
 * password, and nothing that decides what they can do.
 */
@Data
public class AcceptInvitationRequest {

    @NotBlank(message = "The invitation token is required")
    @Size(max = 500)
    private String token;

    @NotBlank(message = "First name is required")
    @Size(max = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100)
    private String lastName;

    @NotBlank(message = "Choose a password")
    @ValidPassword
    private String password;

    @Size(max = 40)
    private String phone;

    @Size(max = 150)
    private String jobTitle;
}
