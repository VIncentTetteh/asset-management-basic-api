package com.assetiq.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.ZoneId;

/** Validates {@link TimeZoneId}. */
public class TimeZoneIdValidator implements ConstraintValidator<TimeZoneId, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return isRegionId(value.trim());
    }

    /** True for an IANA region id (or UTC/GMT) known to this JVM. */
    public static boolean isRegionId(String zone) {
        return ZoneId.getAvailableZoneIds().contains(zone) || "UTC".equals(zone) || "GMT".equals(zone);
    }
}
