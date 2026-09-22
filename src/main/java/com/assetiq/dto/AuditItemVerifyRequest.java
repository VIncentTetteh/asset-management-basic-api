package com.assetiq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Records that an asset was sighted during an audit.
 *
 * <p>{@code scan} is whatever came off the label: an asset QR link, the legacy
 * {@code asset:<uuid>} text, a bare asset id, or the asset tag typed by hand. It
 * is resolved by {@link com.assetiq.assets.AssetQrCodes} first and, failing that,
 * looked up as a tag — so the same endpoint serves the scanner and the search box.
 */
public record AuditItemVerifyRequest(
        @NotBlank(message = "A scanned code or asset tag is required")
        @Size(max = 512)
        String scan,

        /** Where it was actually found. Blank means "where the register says". */
        @Size(max = 255)
        String actualLocation,

        @Size(max = 255)
        String condition,

        @Size(max = 5000)
        String remarks
) {}
