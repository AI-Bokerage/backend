package com.company.audit.spring.wire;

import java.time.Instant;
import java.util.Map;

/**
 * The wire format an external producer (for example, a message published to Rabbit) uses to
 * describe an audit event, before it is mapped into {@code audit-core}'s {@link
 * com.company.audit.core.api.AuditEvent}.
 *
 * <p>{@code actorType} and {@code category} are plain strings here, not the real enums — an
 * unrecognized value from an upstream producer must fail as a clean, specific validation error at
 * the mapping boundary ({@link AuditEventEnvelopeMapper}), not as a raw JSON deserialization
 * failure with a confusing stack trace.
 *
 * <p>{@code headers} is a deliberately generic escape hatch for producer metadata (correlation
 * IDs, trace IDs, producer versions, and anything else no current caller needs) rather than named
 * fields guessed ahead of a real consumer. {@code schemaVersion} is different: unlike a Java
 * interface method, a wire format consumed by external producers is expensive to change after
 * the fact, so it gets a version marker now, the same way the Mongo document did.
 *
 * @param schemaVersion the wire schema version this envelope was produced under
 * @param partitionKey the key identifying which independent hash chain this event belongs to
 * @param eventType a producer-defined type discriminator for this event
 * @param actorType the actor type, as its enum name
 * @param actorId the identifier of the actor that initiated this event
 * @param category the audit category, as its enum name
 * @param occurredAt the instant at which the audited occurrence took place
 * @param payload arbitrary structured detail about the event
 * @param resourceType the type of resource this event concerns, or {@code null} if none
 * @param resourceId the identifier of the resource this event concerns, or {@code null} if none
 * @param reason a human-readable explanation for this event, or {@code null} if none
 * @param headers producer metadata not yet acted on by any consumer, defaulting to empty
 */
public record AuditEventEnvelope(
        int schemaVersion,
        String partitionKey,
        String eventType,
        String actorType,
        String actorId,
        String category,
        Instant occurredAt,
        Map<String, Object> payload,
        String resourceType,
        String resourceId,
        String reason,
        Map<String, String> headers) {

    /** The current schema version producers should write. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /**
     * Normalizes {@code null} {@code payload}/{@code headers} to empty maps.
     */
    public AuditEventEnvelope {
        payload = payload == null ? Map.of() : payload;
        headers = headers == null ? Map.of() : headers;
    }
}
