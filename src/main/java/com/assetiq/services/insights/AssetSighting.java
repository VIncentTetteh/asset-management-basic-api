package com.assetiq.services.insights;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * The last time anybody is known to have actually seen an asset.
 *
 * <p>The distinction this type exists to keep is between <em>observing</em> an
 * asset and <em>editing its record</em>. A bulk import, a category rename or a
 * corrected serial number all touch {@code updatedAt} without anybody going
 * near the thing itself; treating that as evidence of use is how a dashboard
 * ends up confidently reporting that a laptop in daily service has been idle
 * for six months.
 *
 * <p>Only three events in AssetIQ mean somebody was in the same room as the
 * asset: a scan, a checkout or check-in, and a physical audit verification.
 * Those are the sources below. AssetIQ records no usage telemetry, so even
 * these say "last seen", never "last used" — and a caller that has no sighting
 * at all must say so rather than inventing one.
 */
public record AssetSighting(Instant at, Source source) {

    public enum Source {
        /** A QR or barcode scan. */
        SCAN("scanned"),
        /** Handed out to, or handed back by, a person. */
        CHECKOUT("checked out or returned"),
        /** Confirmed present during a physical audit. */
        AUDIT("confirmed by audit");

        private final String verb;

        Source(String verb) {
            this.verb = verb;
        }

        /** Past-tense phrase for a sentence like "Last {@code scanned} 40 days ago". */
        public String verb() {
            return verb;
        }
    }

    /** The later of two sightings; either may be null. */
    public static AssetSighting later(AssetSighting a, AssetSighting b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.at().isAfter(b.at()) ? a : b;
    }

    /** Null-safe constructor: no instant, no sighting. */
    public static AssetSighting of(Instant at, Source source) {
        return at == null ? null : new AssetSighting(at, source);
    }

    public long daysAgo(Instant now) {
        return ChronoUnit.DAYS.between(at, now);
    }

    /** "scanned 42 days ago" — the phrase a user can check against the record. */
    public String describe(Instant now) {
        return "Last " + source.verb() + " " + daysAgo(now) + " days ago";
    }
}
