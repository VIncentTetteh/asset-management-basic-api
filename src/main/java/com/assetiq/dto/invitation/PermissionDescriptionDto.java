package com.assetiq.dto.invitation;

/**
 * One permission, described the way a person would read it.
 *
 * @param key      the authority string, e.g. {@code DISPOSE_ASSET}
 * @param label    short verb phrase — "Dispose of assets"
 * @param summary  one sentence an administrator can act on
 * @param group    the area of the product it belongs to
 * @param write    true when it permits change rather than only reading
 * @param enforced false when no endpoint consults it, so granting it does nothing
 */
public record PermissionDescriptionDto(String key, String label, String summary,
                                       String group, boolean write, boolean enforced) {
}
