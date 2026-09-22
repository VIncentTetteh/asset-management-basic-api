package com.assetiq.validation;

import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every request body is validated, and DTOs that split "required" into the
 * {@link OnCreate} group get it on POST/PUT and not on PATCH.
 *
 * <p>Without this, switching a DTO to {@code @NotBlank(groups = OnCreate.class)}
 * and forgetting one create endpoint would silently stop enforcing required
 * fields there; and a PATCH without {@code @Valid} skips validation entirely.
 */
class ControllerValidationWiringTest {

    @Test
    void requestBodiesAreValidatedWithTheRightGroups() throws Exception {
        List<String> problems = new ArrayList<>();
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        int checked = 0;

        for (BeanDefinition bd : scanner.findCandidateComponents("com.assetiq")) {
            Class<?> controller = Class.forName(bd.getBeanClassName());
            for (Method m : controller.getDeclaredMethods()) {
                String verb = verb(m);
                if (verb == null) continue;
                for (Parameter p : m.getParameters()) {
                    if (!p.isAnnotationPresent(RequestBody.class)
                            || !p.getType().getName().startsWith("com.assetiq.")) {
                        continue;
                    }
                    checked++;
                    String where = controller.getSimpleName() + "." + m.getName() + " (" + verb + ")";
                    Validated validated = p.getAnnotation(Validated.class);
                    boolean valid = p.isAnnotationPresent(Valid.class);
                    boolean onCreate = validated != null && Arrays.asList(validated.value()).contains(OnCreate.class);
                    if (!valid && validated == null) {
                        problems.add(where + ": request body is not validated");
                    } else if (declaresOnCreate(p.getType())) {
                        if (!verb.equals("PATCH") && !onCreate) {
                            problems.add(where + ": " + p.getType().getSimpleName()
                                    + " keeps required fields in OnCreate; use @Validated(OnCreate.class)");
                        }
                        if (verb.equals("PATCH") && onCreate) {
                            problems.add(where + ": a PATCH must not require every field; use @Valid");
                        }
                    }
                }
            }
        }

        assertThat(checked).isGreaterThan(40);
        assertThat(problems).isEmpty();
    }

    private static String verb(Method m) {
        if (m.isAnnotationPresent(PostMapping.class)) return "POST";
        if (m.isAnnotationPresent(PutMapping.class)) return "PUT";
        if (m.isAnnotationPresent(PatchMapping.class)) return "PATCH";
        return null;
    }

    private static boolean declaresOnCreate(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                for (Annotation a : f.getAnnotations()) {
                    try {
                        Method groups = a.annotationType().getMethod("groups");
                        if (Arrays.asList((Class<?>[]) groups.invoke(a)).contains(OnCreate.class)) return true;
                    } catch (ReflectiveOperationException ignored) {
                        // not a constraint annotation
                    }
                }
            }
        }
        return false;
    }
}
