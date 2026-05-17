package com.assetiq.services;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * P0-8: Method- or class-level gate that short-circuits execution when the
 * referenced flag is OFF for the current tenant.
 * <p>
 * Usage:
 * <pre>
 *   &#64;FeatureFlagGate("billing.paystack-momo-channels")
 *   public CheckoutResponse initializeMobileMoneyCheckout(...) { ... }
 * </pre>
 * <p>
 * When the flag is OFF the advice throws {@link FeatureDisabledException},
 * which the global exception handler maps to HTTP 404 (so we never leak the
 * existence of a disabled feature to a customer). See
 * {@code FeatureFlagAspect} for the weaving.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface FeatureFlagGate {

    /** The registered flag key, e.g. {@code billing.ghs-default-currency}. */
    String value();

    /** Override the 404 default with a 501 Not Implemented response instead. */
    boolean throwNotImplemented() default false;
}
