package com.company.audit.core.api;

import com.company.audit.core.api.enums.ActorType;
import com.company.audit.core.api.enums.AuditCategory;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable description of a single occurrence to be recorded in the audit ledger.
 *
 * <p>Instances should not be constructed directly; use {@link #builder()} instead, so that
 * validation always happens through this record's canonical constructor.
 *
 * @param id a caller-assigned identifier for this event
 * @param partitionKey the key identifying which independent hash chain this event belongs to
 * @param eventType a caller-defined type discriminator for this event
 * @param actorType the general category of actor that initiated this event
 * @param actorId the identifier of the actor that initiated this event
 * @param category the general subject-matter category of this event
 * @param occurredAt the instant at which the audited occurrence took place
 * @param payload arbitrary structured detail about the event; {@code null} normalizes to an
 *     empty map
 * @param resourceType the type of resource this event concerns, or {@code null} if none
 * @param resourceId the identifier of the resource this event concerns, or {@code null} if none
 * @param reason a human-readable explanation for this event, or {@code null} if none
 */
public record AuditEvent(
        String id,
        String partitionKey,
        String eventType,
        ActorType actorType,
        String actorId,
        AuditCategory category,
        Instant occurredAt,
        Map<String, Object> payload,
        String resourceType,
        String resourceId,
        String reason) {

    /**
     * Validates non-nullable fields and normalizes a {@code null} payload to an empty map.
     */
    public AuditEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(partitionKey, "partitionKey must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(actorType, "actorType must not be null");
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        payload = payload == null ? Map.of() : payload;
    }

    /**
     * Creates a new builder for constructing an {@code AuditEvent}.
     *
     * @return a new {@link AuditEventBuilder}
     */
    public static AuditEventBuilder builder() {
        return new AuditEventBuilder();
    }
}
