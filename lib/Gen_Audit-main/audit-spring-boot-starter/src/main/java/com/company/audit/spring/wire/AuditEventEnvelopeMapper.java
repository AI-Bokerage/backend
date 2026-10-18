package com.company.audit.spring.wire;

import com.company.audit.core.api.AuditEvent;
import com.company.audit.core.api.enums.ActorType;
import com.company.audit.core.api.enums.AuditCategory;
import java.util.UUID;

/**
 * Converts an inbound {@link AuditEventEnvelope} into {@code audit-core}'s {@link AuditEvent}.
 *
 * <p>{@code actorType} and {@code category} are parsed from their wire strings into the real
 * enums here, not via direct JSON-to-enum deserialization on the envelope itself — this is the
 * boundary where an upstream producer's bug (an unrecognized enum name) becomes a specific,
 * debuggable {@link UnrecognizedEnvelopeValueException} rather than an opaque deserialization
 * failure.
 *
 * <p>{@code headers} and {@code schemaVersion} are read but not yet acted on: they exist on the
 * wire format for forward compatibility, not because this phase has anything to do with them.
 */
public class AuditEventEnvelopeMapper {

    /**
     * Creates a new mapper.
     */
    public AuditEventEnvelopeMapper() {
    }

    /**
     * Converts an envelope into its domain form.
     *
     * @param envelope the inbound envelope
     * @return the equivalent audit event
     * @throws UnrecognizedEnvelopeValueException if {@code actorType} or {@code category} is not
     *     a recognized enum name
     */
    public AuditEvent toDomain(AuditEventEnvelope envelope) {
        return AuditEvent.builder()
                .partitionKey(envelope.partitionKey())
                .eventType(envelope.eventType())
                .actorType(parseEnum(ActorType.class, "actorType", envelope.actorType()))
                .actorId(envelope.actorId())
                .category(parseEnum(AuditCategory.class, "category", envelope.category()))
                .occurredAt(envelope.occurredAt())
                .payload(envelope.payload())
                .resourceType(envelope.resourceType())
                .resourceId(envelope.resourceId())
                .reason(envelope.reason())
                .id(UUID.randomUUID().toString())
                .build();
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumType, String fieldName, String value) {
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new UnrecognizedEnvelopeValueException(fieldName, value, e);
        }
    }
}
