package com.assetiq.config;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.*;

/**
 * Marks a bean or configuration class so that it is only loaded when the
 * application is running in the specified {@link AppMode}.
 *
 * <p><b>Usage — standalone-only beans:</b></p>
 * <pre>{@code
 * @Configuration
 * @ConditionalOnAppMode(AppMode.STANDALONE)
 * public class LicenseConfig { ... }
 * }</pre>
 *
 * <p><b>Usage — cloud-only beans (rare):</b></p>
 * <pre>{@code
 * @Service
 * @ConditionalOnAppMode(AppMode.CLOUD)
 * public class CloudOnlyService { ... }
 * }</pre>
 *
 * <p>When {@code app.mode} is absent the condition defaults to {@code cloud},
 * so standalone-only beans are <em>never</em> loaded in the existing
 * cloud deployment without an explicit opt-in.</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnAppModeCondition.class)
public @interface ConditionalOnAppMode {

    /** The mode this bean or configuration requires. */
    AppMode value();
}
