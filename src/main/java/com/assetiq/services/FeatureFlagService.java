package com.assetiq.services;

import com.assetiq.models.FeatureFlag;
import com.assetiq.models.FeatureFlagOrganisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.FeatureFlagOrganisationRepository;
import com.assetiq.repositories.FeatureFlagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;

/**
 * P0-8: Central feature-flag helper.
 * <p>
 * Evaluation order for {@link #isEnabled(String)}:
 * <ol>
 *   <li>If a per-organisation override row exists for the current tenant, use it verbatim.</li>
 *   <li>Else, if {@code enabled_globally} is true, return true.</li>
 *   <li>Else, if {@code rollout_percentage} &gt; 0, use a stable
 *       MD5(tenantId + key) bucket to gate the flag.</li>
 *   <li>Else, return false.</li>
 * </ol>
 * <p>
 * The service is intentionally DB-backed instead of config-file based:
 * flags can be toggled without a redeploy, which is table-stakes for a
 * multi-tenant SaaS. A Caffeine cache can be layered on in Phase 3 without
 * changing the public API.
 */
@Service
public class FeatureFlagService {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagService.class);

    private final FeatureFlagRepository flagRepository;
    private final FeatureFlagOrganisationRepository overrideRepository;

    public FeatureFlagService(
            FeatureFlagRepository flagRepository,
            FeatureFlagOrganisationRepository overrideRepository) {
        this.flagRepository = flagRepository;
        this.overrideRepository = overrideRepository;
    }

    /** Evaluate a flag for the current tenant (thread-local). */
    public boolean isEnabled(String key) {
        return isEnabledFor(key, TenantContext.getOrganisationId());
    }

    /** Evaluate a flag for an explicit tenant — useful for background jobs. */
    @Transactional(readOnly = true)
    public boolean isEnabledFor(String key, UUID organisationId) {
        Optional<FeatureFlag> maybeFlag = flagRepository.findByKey(key);
        if (maybeFlag.isEmpty()) {
            // Unknown flags are OFF. Log once at DEBUG so noisy callers don't
            // drown out the rest of the signal.
            if (log.isDebugEnabled()) {
                log.debug("[FeatureFlag] '{}' is unregistered; treating as OFF.", key);
            }
            return false;
        }
        FeatureFlag flag = maybeFlag.get();

        if (organisationId != null) {
            Optional<FeatureFlagOrganisation> override =
                    overrideRepository.findByFeatureFlagKeyAndOrganisationId(key, organisationId);
            if (override.isPresent()) {
                return override.get().isEnabled();
            }
        }

        if (flag.isEnabledGlobally()) {
            return true;
        }
        if (flag.getRolloutPercentage() <= 0) {
            return false;
        }
        return isInRolloutBucket(key, organisationId, flag.getRolloutPercentage());
    }

    /**
     * Deterministic bucket: hash(org+key) → 0..99. A tenant with no id (e.g.
     * public/anonymous traffic) always lands in bucket 0 so it sees the most
     * conservative outcome.
     */
    private boolean isInRolloutBucket(String key, UUID organisationId, short percentage) {
        if (organisationId == null) {
            return percentage >= 100;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest((organisationId + ":" + key).getBytes(StandardCharsets.UTF_8));
            int bucket = Math.floorMod(digest[0], 100);
            return bucket < percentage;
        } catch (NoSuchAlgorithmException e) {
            // MD5 is always available in a JRE; falling back to OFF is safer
            // than opening a flag to 100% silently.
            log.error("[FeatureFlag] MD5 unavailable; flag '{}' evaluated to OFF.", key, e);
            return false;
        }
    }
}
