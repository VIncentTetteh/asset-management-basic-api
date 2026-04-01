package com.example.demo.services;

import com.example.demo.enums.AssetStatus;
import com.example.demo.models.Asset;
import com.example.demo.models.User;

public interface AssetStateTransitionService {
    /** Validate and apply a status transition. Throws IllegalStateException if not allowed. */
    Asset transition(Asset asset, AssetStatus newStatus, User actor, String reason);

    /** Check whether the given transition is valid without applying it. */
    boolean isTransitionAllowed(AssetStatus from, AssetStatus to);
}
