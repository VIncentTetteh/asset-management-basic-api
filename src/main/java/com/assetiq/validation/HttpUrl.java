package com.assetiq.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A URL field that clients render as a link (document, receipt, evidence and
 * report URLs): {@code null} or blank is allowed; anything else must be an
 * absolute {@code http} or {@code https} URL with a host.
 *
 * <p>Why: the web app renders these as {@code href}s. A stored {@code javascript:},
 * {@code data:} or {@code vbscript:} URL would run script in the app's origin when
 * clicked (stored XSS). The web app also refuses to link anything but http(s);
 * this keeps such values out of the database for every client. Length stays with
 * the field's {@code @Size}, which mirrors its column.
 *
 * <p>{@link #allowPlainText()} is for fields documented as "a URL or a
 * reference" (e.g. a disposal's compliance document, which may be a certificate
 * number): text with no URL scheme passes, but anything that carries a scheme
 * must still be http(s).
 */
@Documented
@Constraint(validatedBy = HttpUrlValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE,
        ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpUrl {

    String message() default "must be an http:// or https:// URL";

    /** Also accept plain text that has no URL scheme (a reference rather than a link). */
    boolean allowPlainText() default false;

    /** Accept only https (e.g. an OpenID Connect issuer, which the spec requires to be https). */
    boolean httpsOnly() default false;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
