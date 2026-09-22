package com.assetiq.dpa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /dpa/consent. The IP address and user agent kept as evidence are
 * taken from the request itself (trusted-proxy aware), never from the body,
 * where a client could write anything.
 */
public record CreateConsentRequest(
        @NotBlank @Size(max = 100) String purpose,
        boolean granted
) {}
