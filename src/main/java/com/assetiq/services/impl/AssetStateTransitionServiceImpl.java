package com.assetiq.services.impl;

import com.assetiq.enums.AssetStatus;
import com.assetiq.models.Asset;
import com.assetiq.models.User;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.services.AssetStateTransitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class AssetStateTransitionServiceImpl implements AssetStateTransitionService {

    private static final Logger log = LoggerFactory.getLogger(AssetStateTransitionServiceImpl.class);

    /** Allowed forward transitions per source state. */
    private static final Map<AssetStatus, Set<AssetStatus>> ALLOWED = Map.of(
            AssetStatus.PENDING_PROCUREMENT, EnumSet.of(AssetStatus.IN_STOCK),
            AssetStatus.IN_STOCK,            EnumSet.of(AssetStatus.RESERVED, AssetStatus.IN_USE, AssetStatus.DISPOSED),
            AssetStatus.RESERVED,            EnumSet.of(AssetStatus.IN_USE, AssetStatus.IN_STOCK),
            AssetStatus.IN_USE,              EnumSet.of(AssetStatus.MAINTENANCE, AssetStatus.UNDER_REPAIR,
                                                         AssetStatus.RESERVED, AssetStatus.IN_STOCK,
                                                         AssetStatus.MISSING, AssetStatus.DISPOSED, AssetStatus.RETIRED),
            AssetStatus.MAINTENANCE,         EnumSet.of(AssetStatus.IN_USE, AssetStatus.UNDER_REPAIR),
            AssetStatus.UNDER_REPAIR,        EnumSet.of(AssetStatus.IN_USE, AssetStatus.MAINTENANCE,
                                                         AssetStatus.DISPOSED, AssetStatus.RETIRED),
            AssetStatus.MISSING,             EnumSet.of(AssetStatus.IN_USE, AssetStatus.IN_STOCK),
            AssetStatus.DISPOSED,            EnumSet.noneOf(AssetStatus.class),
            AssetStatus.RETIRED,             EnumSet.noneOf(AssetStatus.class)
    );

    private final AssetRepository assetRepository;

    public AssetStateTransitionServiceImpl(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    @Override
    public boolean isTransitionAllowed(AssetStatus from, AssetStatus to) {
        Set<AssetStatus> allowed = ALLOWED.get(from);
        return allowed != null && allowed.contains(to);
    }

    @Override
    public Asset transition(Asset asset, AssetStatus newStatus, User actor, String reason) {
        AssetStatus current = asset.getStatus();

        if (current == newStatus) {
            log.debug("Asset {} is already in status {}; skipping transition.", asset.getId(), current);
            return asset;
        }

        if (!isTransitionAllowed(current, newStatus)) {
            throw new IllegalStateException(
                    String.format("Cannot transition asset '%s' from %s to %s.", asset.getName(), current, newStatus));
        }

        log.info("Transitioning asset {} from {} to {} (actor={}, reason={})",
                asset.getId(), current, newStatus,
                actor != null ? actor.getEmail() : "SYSTEM",
                reason);

        asset.setStatus(newStatus);
        return assetRepository.save(asset);
    }
}
