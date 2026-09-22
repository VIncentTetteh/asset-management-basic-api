package com.assetiq.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A telephone number, in the one shape the whole product accepts.
 *
 * <p>Phone fields used to have a length limit and nothing else, so "n/a" and a
 * pasted paragraph were both stored happily, and the web app enforced nothing
 * either. {@link #PATTERN} is the single rule: the web app's
 * {@code FIELD_LIMITS} phone entries carry the same regex.
 *
 * <p>Absent or blank passes — a phone number is optional everywhere it appears.
 */
@Documented
@Constraint(validatedBy = ValidPhoneValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPhone {

    /**
     * Optional leading +, then a digit or "(", then 5 to 23 more digits, spaces
     * or the usual separators: 6 to 24 characters in all. Deliberately permissive
     * about national formats, strict about anything that is not a phone number.
     */
    String PATTERN = "^\\+?[0-9(][0-9 ()./-]{5,23}$";

    String message() default "must be a phone number, e.g. +233 20 123 4567";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
