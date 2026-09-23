package com.assetiq.license;

import com.assetiq.config.AppMode;
import com.assetiq.config.ConditionalOnAppMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Blocks write operations when the license is in read-only state.
 *
 * <p><strong>Only instantiated when {@code APP_MODE=standalone}.</strong>
 * In cloud mode this filter class is never loaded into the Spring context,
 * so cloud behaviour is completely unchanged.</p>
 *
 * <h2>Enforcement is OFF by default</h2>
 * <p>{@code app.license.enforcement.block-writes} defaults to {@code false}, so
 * this filter logs and lets the request through. Blocking writes is opt-in.</p>
 *
 * <p>Why: the state this filter reads was, until recently, set to read-only
 * whenever {@link LicenseService} could not reach the vendor licence server. A
 * dropped outbound connection at a customer site therefore became an outage of
 * their asset register — every write 402 — for a licence that was valid and said
 * so in its own signature. {@code LicenseService} no longer derives read-only
 * from a network failure, which fixes the cause; leaving the switch off as well
 * means no future change to state derivation can quietly turn a licensing
 * condition into an outage.</p>
 *
 * <p>The self-hosted path that replaced this one,
 * {@link com.assetiq.license.offline.OfflineLicenseService}, does not call home
 * at all and degrades an absent, expired or tampered key to the free tier. This
 * filter is retained because {@code APP_MODE=standalone} installations and the
 * {@code /api/v1/license/status} endpoint still consume {@link LicenseService};
 * deleting it would break them for no gain.</p>
 *
 * <p>Enforcement rules when {@code app.license.enforcement.block-writes=true}:</p>
 * <ul>
 *   <li>GET, HEAD, OPTIONS — always allowed (read access preserved)</li>
 *   <li>POST, PUT, PATCH, DELETE — blocked with HTTP 402 when license is read-only</li>
 *   <li>Paystack webhook, auth, and health endpoints — exempt even in read-only mode</li>
 * </ul>
 */
@Component
@ConditionalOnAppMode(AppMode.STANDALONE)
@Order(10)
public class LicenseGuardFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LicenseGuardFilter.class);

    private static final Set<String> WRITE_METHODS = Set.of(
        HttpMethod.POST.name(), HttpMethod.PUT.name(),
        HttpMethod.PATCH.name(), HttpMethod.DELETE.name()
    );

    /** Endpoints that must remain writable even when the license is read-only. */
    private static final Set<String> EXEMPT_PATHS = Set.of(
        "/api/v1/auth/login",
        "/api/v1/auth/logout",
        "/api/v1/auth/refresh",
        "/api/v1/auth/forgot-password",
        "/api/v1/auth/reset-password",
        "/api/v1/mfa/challenge",
        "/api/v1/billing/webhooks/paystack",
        "/api/v1/license/activate",   // must be writable so admin can enter a new key
        "/actuator/health"
    );

    private final LicenseService licenseService;
    private final ObjectMapper   objectMapper;

    /**
     * Opt-in hard stop. Off by default: a licensing condition must degrade, not
     * take the customer's installation down.
     */
    private final boolean blockWrites;

    public LicenseGuardFilter(LicenseService licenseService, ObjectMapper objectMapper) {
        this(licenseService, objectMapper, false);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LicenseGuardFilter(
            LicenseService licenseService,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Value(
                    "${app.license.enforcement.block-writes:false}") boolean blockWrites) {
        this.licenseService = licenseService;
        this.objectMapper   = objectMapper;
        this.blockWrites    = blockWrites;
        if (blockWrites) {
            log.warn("[LICENSE] Write blocking is ENABLED "
                     + "(app.license.enforcement.block-writes=true). A read-only licence state "
                     + "will return HTTP 402 on every mutating request.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String method = req.getMethod();
        String path   = req.getRequestURI();

        if (WRITE_METHODS.contains(method) && !isExempt(path)) {
            LicenseState state = licenseService.getCurrentState();
            if (state.readOnly()) {
                if (!blockWrites) {
                    // Degrade, never hard-stop. The licence condition is worth a log
                    // line and a banner in the UI (via /api/v1/license/status); it is
                    // not worth refusing to record an asset movement.
                    log.warn("[LICENSE] {} {} allowed despite licence status '{}': write blocking "
                             + "is disabled (app.license.enforcement.block-writes=false). {}",
                             method, path, state.status(),
                             state.message() == null ? "" : state.message());
                    chain.doFilter(req, res);
                    return;
                }
                log.debug("License read-only guard blocked {} {}", method, path);
                res.setStatus(402);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "status",  402,
                    "error",   "LICENSE_READ_ONLY",
                    "message", state.message() != null
                        ? state.message()
                        : "Your license has expired. Renew at https://portal.assetiq.io",
                    "licenseStatus", state.status()
                )));
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private boolean isExempt(String path) {
        return EXEMPT_PATHS.stream().anyMatch(path::startsWith);
    }
}
