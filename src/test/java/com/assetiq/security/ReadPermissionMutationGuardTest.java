package com.assetiq.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A VIEW_* permission is a read grant: no mutating endpoint in any controller may
 * accept one. VIEW_ASSETS used to let any viewer check assets out/in and request
 * transfers; this sweeps every controller so a new endpoint cannot regress it.
 */
@DisplayName("No mutating endpoint accepts a VIEW_* authority")
class ReadPermissionMutationGuardTest {

    private static final Pattern LITERAL = Pattern.compile("'([^']+)'");

    /**
     * Deliberate exceptions: acting on the caller's own notification inbox, and
     * regenerating derived AI insights (no business record changes).
     */
    private static final Set<String> ALLOWED = Set.of(
            "NotificationsController.markAsRead",
            "NotificationsController.markAllAsRead",
            "NotificationsController.deleteNotification",
            "NotificationsController.deleteAllNotifications",
            "NotificationsController.updateNotificationPreferences",
            "AIInsightsController.generate");

    @Test
    void mutationsNeedAWriteAuthority() throws Exception {
        List<String> offenders = new ArrayList<>();
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (BeanDefinition def : scanner.findCandidateComponents("com.assetiq")) {
            Class<?> controller = Class.forName(def.getBeanClassName());
            for (Method m : controller.getDeclaredMethods()) {
                if (!isMutation(m)) continue;
                // A method without its own @PreAuthorize inherits the class-level one.
                PreAuthorize rule = m.isAnnotationPresent(PreAuthorize.class)
                        ? m.getAnnotation(PreAuthorize.class) : controller.getAnnotation(PreAuthorize.class);
                if (rule == null) continue;
                String key = controller.getSimpleName() + "." + m.getName();
                if (ALLOWED.contains(key)) continue;
                for (String authority : literals(rule.value())) {
                    if (authority.startsWith("VIEW_")) offenders.add(key + " accepts " + authority);
                }
            }
        }
        assertThat(offenders).isEmpty();
    }

    private static boolean isMutation(Method m) {
        return m.isAnnotationPresent(PostMapping.class) || m.isAnnotationPresent(PutMapping.class)
                || m.isAnnotationPresent(PatchMapping.class) || m.isAnnotationPresent(DeleteMapping.class);
    }

    private static List<String> literals(String expression) {
        Matcher m = LITERAL.matcher(expression);
        List<String> out = new ArrayList<>();
        while (m.find()) out.add(m.group(1));
        return out;
    }
}
