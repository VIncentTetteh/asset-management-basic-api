package com.assetiq.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Blank, or an IANA time-zone region id that {@link java.time.ZoneId#of} accepts
 * (e.g. {@code Africa/Accra}, {@code UTC}). Fixed offsets such as {@code +02:00}
 * are refused: a region id keeps daylight-saving rules.
 */
@Documented
@Constraint(validatedBy = TimeZoneIdValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TimeZoneId {

    String message() default "must be a time zone such as Africa/Accra";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
