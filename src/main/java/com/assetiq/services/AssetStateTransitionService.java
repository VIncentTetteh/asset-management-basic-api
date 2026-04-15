package com.assetiq.services;

import com.assetiq.enums.AssetStatus;
import com.assetiq.models.Asset;
import com.assetiq.models.User;

public interface AssetStateTransitionService {
    /** Validate and apply a status transition. Throws IllegalStateException if not allowed. */
    Asset transition(Asset asset, AssetStatus newStatus, User actor, String reason);

    /** Check whether the given transition is valid without applying it. */
    boolean isTransitionAllowed(AssetStatus from, AssetStatus to);
}
