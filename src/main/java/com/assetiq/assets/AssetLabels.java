package com.assetiq.assets;

import com.assetiq.models.Asset;

/**
 * Human-readable asset references for notification and audit text. Optional
 * parts are omitted rather than rendered as {@code "null"}.
 */
public final class AssetLabels {

    private AssetLabels() {
    }

    /** {@code Asset 'Laptop' (tag: IT-001)}, or {@code Asset 'Laptop'} when the asset has no tag. */
    public static String describe(Asset asset) {
        return describe(asset.getName(), asset.getAssetTag());
    }

    /** As {@link #describe(Asset)}, from the name and tag directly. */
    public static String describe(String name, String assetTag) {
        String label = "Asset '" + (name != null ? name : "Unnamed") + "'";
        return hasText(assetTag) ? label + " (tag: " + assetTag.trim() + ")" : label;
    }

    /** {@code " (tag: IT-001)"} or an empty string when there is no tag. */
    public static String tagClause(String assetTag) {
        return hasText(assetTag) ? " (tag: " + assetTag.trim() + ")" : "";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
