package com.assetiq.imports;

import com.assetiq.validation.OnCreate;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.groups.Default;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Set;

/**
 * Runs the same bean constraints an API caller would hit against a row built by an
 * import.
 *
 * <p>Import handlers construct DTOs directly rather than going through a controller, so
 * without this a spreadsheet could put a malformed email or an out-of-range value into a
 * record that the REST API would have rejected. Reusing the DTO's own annotations — with
 * the {@link OnCreate} group, exactly as the create endpoints do — keeps the import from
 * becoming a side door with weaker rules.</p>
 *
 * <p>The first violation is reported as a {@link FieldValidationException} on its
 * property, so the engine can name the user's own column.</p>
 */
@Component
public class ImportBeanValidator {

    private final Validator validator;

    public ImportBeanValidator(Validator validator) {
        this.validator = validator;
    }

    public <T> void validateForCreate(T dto) {
        validate(dto, Default.class, OnCreate.class);
    }

    public <T> void validateForUpdate(T dto) {
        validate(dto, Default.class);
    }

    private <T> void validate(T dto, Class<?>... groups) {
        Set<ConstraintViolation<T>> violations = validator.validate(dto, groups);
        if (violations.isEmpty()) return;
        // Deterministic choice of which violation to report, so the same bad row always
        // produces the same message.
        ConstraintViolation<T> first = violations.stream()
                .min(Comparator.comparing(v -> v.getPropertyPath().toString()))
                .orElseThrow();
        throw new FieldValidationException(first.getPropertyPath().toString(), first.getMessage());
    }
}
