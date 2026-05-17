package com.assetiq.services;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * P0-8: AOP weaving for {@link FeatureFlagGate}. Intercepts method invocations
 * annotated either on the method directly or inherited from the enclosing class
 * and delegates to {@link FeatureFlagService}. If the flag is OFF,
 * {@link FeatureDisabledException} is thrown instead of running the method.
 * <p>
 * Spring AOP is already on the classpath via
 * {@code spring-boot-starter-aop}, so no extra wiring is needed.
 */
@Aspect
@Component
public class FeatureFlagAspect {

    private final FeatureFlagService featureFlagService;

    public FeatureFlagAspect(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    @Around("@annotation(com.assetiq.services.FeatureFlagGate) "
            + "|| @within(com.assetiq.services.FeatureFlagGate)")
    public Object enforceFeatureFlag(ProceedingJoinPoint pjp) throws Throwable {
        FeatureFlagGate gate = resolveAnnotation(pjp);
        if (gate == null) {
            return pjp.proceed();
        }
        if (!featureFlagService.isEnabled(gate.value())) {
            throw new FeatureDisabledException(gate.value(), gate.throwNotImplemented());
        }
        return pjp.proceed();
    }

    /**
     * Method-level annotations take precedence over class-level ones, mirroring
     * the usual Spring annotation-resolution rules.
     */
    private FeatureFlagGate resolveAnnotation(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();

        FeatureFlagGate onMethod = AnnotationUtils.findAnnotation(method, FeatureFlagGate.class);
        if (onMethod != null) {
            return onMethod;
        }
        Class<?> targetClass = pjp.getTarget() != null
                ? pjp.getTarget().getClass()
                : method.getDeclaringClass();
        return AnnotationUtils.findAnnotation(targetClass, FeatureFlagGate.class);
    }
}
