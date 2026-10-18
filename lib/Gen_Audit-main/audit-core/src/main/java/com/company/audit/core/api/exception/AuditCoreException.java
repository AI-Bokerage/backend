package com.company.audit.core.api.exception;

/**
 * Abstract base class for all unchecked exceptions raised by this library.
 */
public abstract class AuditCoreException extends RuntimeException {

    /**
     * Creates an exception with the given message.
     *
     * @param message a human-readable description of the failure
     */
    protected AuditCoreException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given message and cause.
     *
     * @param message a human-readable description of the failure
     * @param cause the underlying cause
     */
    protected AuditCoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
