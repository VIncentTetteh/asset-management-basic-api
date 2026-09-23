package com.assetiq.dto.invitation;

import com.assetiq.validation.NullOrNotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * What an administrator supplies to invite a colleague.
 *
 * <p>Notably absent: organisation. The invitation is always issued into the
 * caller's own tenant, taken from the authenticated context — accepting an
 * organisation here would be an invitation to point it somewhere else.
 */
@Data
public class InviteUserRequest {

    @NotBlank(message = "An email address is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 255)
    private String email;

    /** Must name a role in the caller's own organisation. */
    @NotNull(message = "Choose the role this person should have")
    private UUID roleId;

    private UUID departmentId;

    /** Optional: pre-fills the acceptance form, which the invitee may correct. */
    @NullOrNotBlank
    @Size(max = 100)
    private String firstName;

    @NullOrNotBlank
    @Size(max = 100)
    private String lastName;

    @Size(max = 150)
    private String jobTitle;

    /** Optional line from the inviter, shown on the acceptance screen. */
    @Size(max = 500)
    private String note;
}
