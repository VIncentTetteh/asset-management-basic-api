package com.assetiq.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative cross-tenant access guard.
 *
 * <p>Place on any controller method that receives an {@code organisationId} parameter.
 * The {@link com.assetiq.security.aspect.TenantEnforcementAspect} will automatically
 * verify that the supplied organisationId matches the current thread's
 * {@link com.assetiq.multitenancy.TenantContext} before the method body executes.
 *
 * <pre>{@code
 * @PostMapping
 * @PreAuthorize("hasAnyAuthority('MANAGE_ROLES')")
 * @EnforceTenant                              // ← replaces manual requireSameOrganisation()
 * public ResponseEntity<RoleDto> createRole(
 *         @Valid @RequestBody RoleDto roleDto,
 *         @RequestParam UUID organisationId) { ... }
 * }</pre>
 *
 * @see com.assetiq.security.aspect.TenantEnforcementAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnforceTenant {

    /**
     * The name of the method parameter that holds the organisation UUID.
     * Defaults to {@code "organisationId"} which matches the convention used across
     * all controllers. Override when the parameter name differs.
     */
    String param() default "organisationId";
}
