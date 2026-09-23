package com.assetiq.services.insights;

import com.assetiq.models.Organisation;
import com.assetiq.repositories.AuditItemRepository;
import com.assetiq.repositories.CheckoutRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * When each of a tenant's assets was last actually seen.
 *
 * <p>Builds the whole tenant's sighting map in two grouped queries — latest
 * handling per asset, latest audit verification per asset — rather than asking
 * per asset. The third source, the asset's own {@code lastScannedAt}, is
 * already on the row the caller is holding, so it is merged in by
 * {@link #sightingFor}.
 *
 * <p>Cost: two {@code GROUP BY asset_id} scans of the tenant's checkout and
 * audit-item tables. Both are read once per dashboard request or per nightly
 * run, never once per asset.
 */
@Service
@Transactional(readOnly = true)
public class AssetSightingService {

    private final CheckoutRecordRepository checkoutRepository;
    private final AuditItemRepository auditItemRepository;

    public AssetSightingService(CheckoutRecordRepository checkoutRepository,
                                AuditItemRepository auditItemRepository) {
        this.checkoutRepository = checkoutRepository;
        this.auditItemRepository = auditItemRepository;
    }

    /**
     * Latest sighting per asset from the records that are not on the asset row:
     * checkouts and audits. Assets absent from the map have neither.
     */
    public Map<UUID, AssetSighting> sightingsFor(Organisation org) {
        Map<UUID, AssetSighting> sightings = new HashMap<>();

        for (Object[] row : checkoutRepository.findLatestHandlingPerAsset(org)) {
            UUID assetId = (UUID) row[0];
            AssetSighting handling = AssetSighting.later(
                    AssetSighting.of(asInstant(row[1]), AssetSighting.Source.CHECKOUT),
                    AssetSighting.of(asInstant(row[2]), AssetSighting.Source.CHECKOUT));
            merge(sightings, assetId, handling);
        }

        for (Object[] row : auditItemRepository.findLatestVerificationPerAsset(org)) {
            merge(sightings, (UUID) row[0],
                    AssetSighting.of(asInstant(row[1]), AssetSighting.Source.AUDIT));
        }
        return sightings;
    }

    /**
     * The asset's last sighting: its own scan, or a checkout or audit from
     * {@code sightings}, whichever is later. Null when nobody has ever recorded
     * seeing it — which callers must report as "no recorded sighting", not as
     * "seen a long time ago".
     */
    public static AssetSighting sightingFor(UUID assetId, Instant lastScannedAt,
                                            Map<UUID, AssetSighting> sightings) {
        return AssetSighting.later(
                AssetSighting.of(lastScannedAt, AssetSighting.Source.SCAN),
                sightings.get(assetId));
    }

    private void merge(Map<UUID, AssetSighting> sightings, UUID assetId, AssetSighting candidate) {
        if (assetId == null || candidate == null) {
            return;
        }
        sightings.merge(assetId, candidate, AssetSighting::later);
    }

    /**
     * A return date is a day, not a moment. It is read as the end of that day in
     * UTC, which is the latest the asset can have been handed back — the
     * conservative choice, because it never makes an asset look less recently
     * seen than it was.
     */
    private Instant asInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof LocalDate date) {
            return date.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
        }
        return null;
    }
}
