package com.assetiq.exceptions;

import jakarta.persistence.EntityNotFoundException;

/**
 * Thrown when a requested resource does not exist (or has been soft-deleted).
 * Extends {@link EntityNotFoundException} so the existing
 * {@link com.assetiq.controllers.v1.GlobalExceptionHandler} maps it to HTTP 404
 * without any additional handler registration.
 */
public class ResourceNotFoundException extends EntityNotFoundException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
