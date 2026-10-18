package com.company.audit.spring.wire;

/**
 * Thrown when an {@link AuditEventEnvelope} field that should map to an {@code audit-core} enum
 * (such as {@code actorType} or {@code category}) holds a value that enum has no member for.
 *
 * <p>This exists so an upstream producer's bug — a typo'd or stale enum name — surfaces as a
 * clean, specific, easily greppable error at the mapping boundary, rather than a raw
 * {@link IllegalArgumentException} from {@code Enum.valueOf} with no context about which envelope
 * field or message caused it.
 */
public final class UnrecognizedEnvelopeValueException extends RuntimeException {

    /**
     * Creates an exception describing the unrecognized value.
     *
     * @param fieldName the envelope field that held the unrecognized value
     * @param value the unrecognized value itself
     * @param cause the underlying enum-parsing failure
     */
    public UnrecognizedEnvelopeValueException(String fieldName, String value, Throwable cause) {
        super("Unrecognized value for envelope field '" + fieldName + "': '" + value + "'", cause);
    }
}
