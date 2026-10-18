package com.company.audit.core.api.exception;

/**
 * Thrown when an event payload cannot be converted into canonical JSON form.
 *
 * <p>This is raised internally by the canonical JSON serializer, but is declared in this
 * package because it can propagate out of {@link com.company.audit.core.api.AuditAppender#append}
 * as part of the public contract.
 */
public final class CanonicalizationException extends AuditCoreException {

    /**
     * Creates an exception with the given message and cause.
     *
     * @param message a human-readable description of the failure
     * @param cause the underlying cause
     */
    public CanonicalizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
