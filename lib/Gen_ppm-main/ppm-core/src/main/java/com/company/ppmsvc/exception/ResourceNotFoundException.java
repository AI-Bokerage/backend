package com.company.ppmsvc.exception;

import java.util.UUID;

/**
 * Thrown when a requested resource cannot be found.
 * Maps to HTTP {@code 404 Not Found}.
 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String detail) {
        super(ErrorCode.RESOURCE_NOT_FOUND, detail);
    }

    public ResourceNotFoundException(String resourceType, UUID id) {
        super(ErrorCode.RESOURCE_NOT_FOUND, resourceType + " not found with id: " + id);
    }

    public ResourceNotFoundException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }
}
