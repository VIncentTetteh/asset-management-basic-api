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

    /** Terminal states: nothing leaves them, and everything else can reach them. */
    private static final Set<AssetStatus> TERMINAL = EnumSet.of(AssetStatus.DISPOSED, AssetStatus.RETIRED);

    /**
     * Allowed forward transitions per source state.
     *
     * <p>Maintenance and disposal used to set the status directly rather than go
     * through here, so the map only described the paths checkout used. It now
     * covers the whole lifecycle: an in-stock or reserved asset can go in for
     * maintenance and come back, and any live asset can be disposed of or
     * retired. The invariant that matters is unchanged — DISPOSED and RETIRED are
     * terminal.
     */
    private static final Map<AssetStatus, Set<AssetStatus>> ALLOWED = Map.of(
            AssetStatus.PENDING_PROCUREMENT, EnumSet.of(AssetStatus.IN_STOCK,
                                                         AssetStatus.DISPOSED, AssetStatus.RETIRED),
            AssetStatus.IN_STOCK,            EnumSet.of(AssetStatus.RESERVED, AssetStatus.IN_USE,
                                                         AssetStatus.MAINTENANCE, AssetStatus.UNDER_REPAIR,
                                                         AssetStatus.MISSING,
                                                         AssetStatus.DISPOSED, AssetStatus.RETIRED),
            AssetStatus.RESERVED,            EnumSet.of(AssetStatus.IN_USE, AssetStatus.IN_STOCK,
                                                         AssetStatus.MAINTENANCE, AssetStatus.UNDER_REPAIR,
                                                         AssetStatus.MISSING,
                                                         AssetStatus.DISPOSED, AssetStatus.RETIRED),
            AssetStatus.IN_USE,              EnumSet.of(AssetStatus.MAINTENANCE, AssetStatus.UNDER_REPAIR,
                                                         AssetStatus.RESERVED, AssetStatus.IN_STOCK,
                                                         AssetStatus.MISSING, AssetStatus.DISPOSED, AssetStatus.RETIRED),
            AssetStatus.MAINTENANCE,         EnumSet.of(AssetStatus.IN_USE, AssetStatus.IN_STOCK,
                                                         AssetStatus.RESERVED, AssetStatus.UNDER_REPAIR,
                                                         AssetStatus.MISSING,
                                                         AssetStatus.DISPOSED, AssetStatus.RETIRED),
            AssetStatus.UNDER_REPAIR,        EnumSet.of(AssetStatus.IN_USE, AssetStatus.IN_STOCK,
                                                         AssetStatus.RESERVED, AssetStatus.MAINTENANCE,
                                                         AssetStatus.MISSING,
                                                         AssetStatus.DISPOSED, AssetStatus.RETIRED),
            AssetStatus.MISSING,             EnumSet.of(AssetStatus.IN_USE, AssetStatus.IN_STOCK,
                                                         AssetStatus.RESERVED,
                                                         AssetStatus.DISPOSED, AssetStatus.RETIRED),
            AssetStatus.DISPOSED,            EnumSet.noneOf(AssetStatus.class),
            AssetStatus.RETIRED,             EnumSet.noneOf(AssetStatus.class)
    );

    private final AssetRepository assetRepository;

    public AssetStateTransitionServiceImpl(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    @Override
    public boolean isTransitionAllowed(AssetStatus from, AssetStatus to) {
        if (from != null && TERMINAL.contains(from)) return false;
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
