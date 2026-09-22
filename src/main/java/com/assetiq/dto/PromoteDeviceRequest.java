package com.assetiq.dto;

import com.assetiq.validation.NullOrNotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Optional body of POST /discovery/devices/{id}/promote: how the new asset is
 * registered. Every field may be omitted; the name defaults to the device's
 * hostname, else its IP address.
 */
public record PromoteDeviceRequest(
        @NullOrNotBlank @Size(max = 255) String name,
        UUID categoryId,
        UUID locationId) {
}
