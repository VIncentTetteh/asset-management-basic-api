package com.assetiq.license;

import com.assetiq.config.AppMode;
import com.assetiq.config.ConditionalOnAppMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * Enforces plan limits defined in the license key payload for standalone deployments.
 *
 * <p>Only loaded when {@code APP_MODE=standalone}. In cloud mode the existing
 * {@code UsageLimitServiceImpl} continues to use Paystack subscription limits.</p>
 *
 * <p>Integrated into {@code UsageLimitServiceImpl} via
 * {@code Optional<LicensePlanLimitsService>} — present only in standalone mode.</p>
 */
@Service
@ConditionalOnAppMode(AppMode.STANDALONE)
public class LicensePlanLimitsService {

    private static final Logger log = LoggerFactory.getLogger(LicensePlanLimitsService.class);

    /** Warn admin when usage reaches this fraction of the limit. */
    private static final double WARN_THRESHOLD = 0.80;

    private final LicenseService licenseService;

    public LicensePlanLimitsService(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

    /**
     * Asserts that the current asset count is below the license limit.
     *
     * @param currentCount number of existing assets in the org
     * @throws AccessDeniedException if the limit is exceeded
     */
    public void assertCanCreateAsset(long currentCount) {
        int limit = getLimit("assets");
        if (limit < 0) return; // -1 = unlimited (Enterprise plan)
        check("Asset", currentCount, limit);
    }

    /**
     * Asserts that the current user count is below the license limit.
     *
     * @param currentCount number of existing users in the org
     * @throws AccessDeniedException if the limit is exceeded
     */
    public void assertCanCreateUser(long currentCount) {
        int limit = getLimit("users");
        if (limit < 0) return;
        check("User", currentCount, limit);
    }

    /**
     * Asserts that the current department count is below the license limit.
     *
     * @param currentCount number of existing departments in the org
     * @throws AccessDeniedException if the limit is exceeded
     */
    public void assertCanCreateDepartment(long currentCount) {
        int limit = getLimit("departments");
        if (limit < 0) return;
        check("Department", currentCount, limit);
    }

    /**
     * Returns true if a given feature is enabled in the license.
     * Always returns true if the feature is absent from the payload (fail-open).
     */
    public boolean isFeatureEnabled(String featureKey) {
        LicenseState state = licenseService.getCurrentState();
        Map<String, Object> features = state.features();
        if (features == null || !features.containsKey(featureKey)) return true;
        Object val = features.get(featureKey);
        return !(val instanceof Boolean b) || b;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void check(String resourceName, long current, int limit) {
        if (current >= limit) {
            throw new AccessDeniedException(
                resourceName + " limit reached for your license plan (" + limit + "). " +
                "Upgrade your plan at https://portal.assetiq.io"
            );
        }
        // Add warning header when approaching 80% of limit
        if ((double) current / limit >= WARN_THRESHOLD) {
            addWarningHeader(resourceName, current, limit);
        }
    }

    private int getLimit(String key) {
        LicenseState state = licenseService.getCurrentState();
        Map<String, Object> limits = state.limits();
        if (limits == null || !limits.containsKey(key)) return Integer.MAX_VALUE;
        Object val = limits.get(key);
        return val instanceof Number n ? n.intValue() : Integer.MAX_VALUE;
    }

    private void addWarningHeader(String resource, long current, int limit) {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                attrs.getResponse().addHeader(
                    "X-License-Warning",
                    resource + " usage at " + current + "/" + limit + " (" +
                    Math.round((double) current / limit * 100) + "%) — approaching plan limit"
                );
            }
        } catch (Exception ignored) {}
    }
}
