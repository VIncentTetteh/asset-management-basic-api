package com.assetiq.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

/**
 * Spring {@link Condition} that matches when the current {@code app.mode}
 * property equals the mode declared on {@link ConditionalOnAppMode}.
 *
 * <p>Comparison is case-insensitive. Defaults to {@code cloud} when the
 * property is absent, ensuring standalone-only beans are never loaded in
 * the existing cloud deployment.</p>
 */
public class OnAppModeCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(
                ConditionalOnAppMode.class.getName()
        );
        if (attributes == null) return false;

        String required = ((AppMode) attributes.get("value")).name().toLowerCase();
        String actual   = context.getEnvironment()
                                 .getProperty("app.mode", "cloud")
                                 .trim()
                                 .toLowerCase();
        return required.equals(actual);
    }
}
