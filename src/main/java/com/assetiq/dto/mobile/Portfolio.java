package com.assetiq.dto.mobile;

import java.util.Map;

/**
 * The tenant's live (not soft-deleted) assets.
 *
 * @param total    all live assets
 * @param byStatus live assets per {@code AssetStatus} name; statuses with no
 *                 assets are absent rather than zero
 */
public record Portfolio(long total, Map<String, Long> byStatus) {
}
