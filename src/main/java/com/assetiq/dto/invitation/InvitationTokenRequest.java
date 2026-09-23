package com.assetiq.dto.invitation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Carries an invitation token for lookup.
 *
 * <p>A POST body rather than a query string on purpose: a token in a URL ends up
 * in access logs, browser history and any referrer header the acceptance page
 * emits, and this one is a credential.
 */
@Data
public class InvitationTokenRequest {

    @NotBlank(message = "The invitation token is required")
    @Size(max = 500)
    private String token;
}
