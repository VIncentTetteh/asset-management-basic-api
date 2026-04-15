package com.assetiq.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Reads and exposes the current {@link AppMode}.
 *
 * <p>Inject this bean wherever you need to branch on cloud vs. standalone
 * behaviour at runtime. For compile-time conditional bean loading prefer
 * {@link ConditionalOnAppMode}.</p>
 *
 * <pre>{@code
 * @Autowired AppModeProperties appMode;
 *
 * if (appMode.isStandalone()) {
 *     // standalone-specific logic
 * }
 * }</pre>
 */
@Configuration
public class AppModeProperties {

    private static final Logger log = LoggerFactory.getLogger(AppModeProperties.class);

    @Value("${app.mode:cloud}")
    private String rawMode;

    private AppMode mode;

    @PostConstruct
    void init() {
        this.mode = AppMode.from(rawMode);
        log.info("=======================================================");
        log.info("  AssetIQ starting in {} mode", mode);
        if (mode == AppMode.STANDALONE) {
            log.info("  License enforcement is ACTIVE");
        } else {
            log.info("  License enforcement is DISABLED (cloud mode)");
        }
        log.info("=======================================================");
    }

    /** Returns the current deployment mode. */
    public AppMode getMode() {
        return mode;
    }

    /** {@code true} when running as vendor-hosted SaaS. */
    public boolean isCloud() {
        return mode == AppMode.CLOUD;
    }

    /** {@code true} when running as a self-hosted on-premise installation. */
    public boolean isStandalone() {
        return mode == AppMode.STANDALONE;
    }
}
