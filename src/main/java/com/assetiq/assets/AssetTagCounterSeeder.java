package com.assetiq.assets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Creates the {@code asset_tag_sequence} row for an organisation and prefix the
 * first time a tag is generated for it.
 *
 * <p>Separate from {@link AssetTagAllocator} so the insert runs in its own
 * transaction: two requests can reach this at the same moment and one of them
 * loses the primary key. In PostgreSQL a failed statement aborts the whole
 * transaction, so that loss has to be contained here rather than taking the
 * asset insert down with it.
 */
@Component
public class AssetTagCounterSeeder {

    private static final Logger log = LoggerFactory.getLogger(AssetTagCounterSeeder.class);

    private final JdbcTemplate jdbc;

    public AssetTagCounterSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Ensures a counter exists, starting at {@code highestExisting} so the first
     * number handed out is the one after the highest tag already in use.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void seedIfMissing(UUID organisationId, String prefix, long highestExisting) {
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM asset_tag_sequence WHERE organisation_id = ? AND prefix = ?",
                Integer.class, organisationId, prefix);
        if (existing != null && existing > 0) return;
        try {
            jdbc.update("INSERT INTO asset_tag_sequence (organisation_id, prefix, next_number) VALUES (?, ?, ?)",
                    organisationId, prefix, highestExisting);
        } catch (DataIntegrityViolationException raced) {
            // Another request seeded the same counter first; theirs is as good as ours.
            log.debug("Asset tag counter {}/{} was seeded concurrently", organisationId, prefix);
        }
    }
}
