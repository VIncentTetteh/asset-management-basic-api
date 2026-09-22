package com.assetiq.validation;

import jakarta.validation.groups.Default;

/**
 * Validation group for requests that must carry every required field: POST
 * (create) and PUT (full replace).
 *
 * <p>DTOs shared with a PATCH endpoint put their "required" constraints in this
 * group ({@code @NotBlank(groups = OnCreate.class)}) and keep everything else
 * (sizes, ranges, {@link NullOrNotBlank}) in the default group. Because this
 * interface extends {@link Default}, {@code @Validated(OnCreate.class)} checks
 * both, while a PATCH annotated plain {@code @Valid} checks only the default
 * group: present values must be valid, absent ones are left unchanged.
 */
public interface OnCreate extends Default {
}
