package com.company.audit.core.api.exception;

/**
 * Thrown when an append operation exhausts its retry budget against
 * {@link com.company.audit.core.port.ChainRepository}, or when a detected chain inconsistency is
 * severe enough that the operation must abort rather than proceed.
 */
public final class ChainIntegrityException extends AuditCoreException {

    /**
     * Creates an exception with the given message.
     *
     * @param message a human-readable description of the failure
     */
    public ChainIntegrityException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given message and cause.
     *
     * @param message a human-readable description of the failure
     * @param cause the underlying cause
     */
    public ChainIntegrityException(String message, Throwable cause) {
        super(message, cause);
    }
}
