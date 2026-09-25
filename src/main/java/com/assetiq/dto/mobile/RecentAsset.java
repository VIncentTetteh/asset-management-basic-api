package com.assetiq.dto.mobile;

import com.assetiq.enums.AssetStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the Home screen's "recently updated" list: just enough to render
 * the row and link to the asset. Nulls are kept so every row has the same keys.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RecentAsset(UUID id, String name, String assetTag, String status, Instant updatedAt) {

    /** Used by the JPQL constructor expression, which hands over the enum. */
    public RecentAsset(UUID id, String name, String assetTag, AssetStatus status, Instant updatedAt) {
        this(id, name, assetTag, status != null ? status.name() : null, updatedAt);
    }
}
