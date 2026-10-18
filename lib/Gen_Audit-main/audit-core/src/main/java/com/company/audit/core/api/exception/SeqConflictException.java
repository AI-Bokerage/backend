package com.company.audit.core.api.exception;

/**
 * Thrown by {@link com.company.audit.core.port.ChainRepository#append} when an append targets a
 * {@code (partitionKey, seq)} pair that already exists.
 *
 * <p>This is a checked exception because it represents an expected, retryable condition: under
 * concurrent appends to the same partition, losing a race for the next sequence number is a
 * normal occurrence that callers are expected to handle by retrying.
 */
public final class SeqConflictException extends Exception {

    /**
     * Creates an exception with the given message.
     *
     * @param message a human-readable description of the conflict
     */
    public SeqConflictException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given message and cause.
     *
     * @param message a human-readable description of the conflict
     * @param cause the underlying cause, such as a storage-layer unique-constraint violation
     */
    public SeqConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
