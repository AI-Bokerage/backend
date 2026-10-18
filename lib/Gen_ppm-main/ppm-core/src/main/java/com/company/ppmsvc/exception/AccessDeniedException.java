package com.company.ppmsvc.exception;

/**
 * Thrown when the authenticated principal lacks sufficient permissions.
 * Maps to HTTP {@code 403 Forbidden}.
 *
 * <p>Distinct from Spring Security's {@code AccessDeniedException} so the
 * domain layer remains framework-independent.
 */
public class AccessDeniedException extends BusinessException {

    public AccessDeniedException() {
        super(ErrorCode.ACCESS_DENIED);
    }

    public AccessDeniedException(String detail) {
        super(ErrorCode.ACCESS_DENIED, detail);
    }
}
