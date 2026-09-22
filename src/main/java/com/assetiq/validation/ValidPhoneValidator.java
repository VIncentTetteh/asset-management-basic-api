package com.assetiq.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/** Applies {@link ValidPhone#PATTERN}; null or blank passes. */
public class ValidPhoneValidator implements ConstraintValidator<ValidPhone, String> {

    private static final Pattern PHONE = Pattern.compile(ValidPhone.PATTERN);

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return true;
        return PHONE.matcher(value.trim()).matches();
    }
}
