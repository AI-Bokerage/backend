package com.company.audit.spring.persistence.jpa.mapper;

import com.company.audit.core.api.AuditEvent;
import com.company.audit.core.api.AuditEventBuilder;
import com.company.audit.core.api.ChainedRecord;
import com.company.audit.core.api.HashValue;
import com.company.audit.core.api.enums.ActorType;
import com.company.audit.core.api.enums.AuditCategory;
import com.company.audit.spring.persistence.jpa.entity.AuditImmutableEntity;
import java.util.Map;
import java.util.UUID;

/**
 * Converts between {@link ChainedRecord} and {@link AuditImmutableEntity}.
 *
 * <p>This is the only place outside {@link HashValue} itself where raw hash bytes should appear:
 * every hash is unwrapped via {@link HashValue#bytes()} on the way in and rewrapped via
 * {@link HashValue#of(byte[])} on the way out.
 *
 * <p>Not component-scanned: a starter should not rely on a consuming application's component
 * scan reaching into its internal packages. This is registered explicitly as a bean by
 * {@link com.company.audit.spring.autoconfigure.AuditJpaAutoConfiguration}.
 */
public class AuditImmutableMapper {

    /**
     * Creates a new mapper.
     */
    public AuditImmutableMapper() {
    }

    /**
     * Converts a chained record into its persistable entity form.
     *
     * <p>The entity's primary key is parsed from {@code record.event().id()}; if that string is
     * not a valid UUID, this throws — that's a caller error, not something this mapper recovers
     * from.
     *
     * @param record the chained record to convert
     * @return the equivalent JPA entity
     */
    public AuditImmutableEntity toEntity(ChainedRecord record) {
        AuditEvent event = record.event();
        return new AuditImmutableEntity(
                UUID.fromString(event.id()),
                event.partitionKey(),
                record.seq(),
                event.eventType(),
                event.actorType().name(),
                event.actorId(),
                event.category().name(),
                event.occurredAt(),
                event.resourceType(),
                event.resourceId(),
                event.reason(),
                record.payloadHash().bytes(),
                record.prevEventHash().bytes(),
                record.eventHash().bytes(),
                record.recordedAt());
    }

    /**
     * Converts a persisted entity back into its domain form.
     *
     * <p>The reconstructed {@link AuditEvent#payload()} is always empty: the raw payload is
     * never stored in {@code audit_immutable}, only its hash.
     *
     * @param entity the entity to convert
     * @return the equivalent chained record
     */
    public ChainedRecord toDomain(AuditImmutableEntity entity) {
        AuditEvent event = AuditEvent.builder()
                .id(entity.getAuditId().toString())
                .partitionKey(entity.getPartitionKey())
                .eventType(entity.getEventType())
                .actorType(ActorType.valueOf(entity.getActorType()))
                .actorId(entity.getActorId())
                .category(AuditCategory.valueOf(entity.getCategory()))
                .occurredAt(entity.getOccurredAt())
                .payload(Map.of())
                .resourceType(entity.getResourceType())
                .resourceId(entity.getResourceId())
                .reason(entity.getReason())
                .build();
        return new ChainedRecord(
                event,
                entity.getSeq(),
                HashValue.of(entity.getPrevEventHash()),
                HashValue.of(entity.getPayloadHash()),
                HashValue.of(entity.getEventHash()),
                entity.getRecordedAt());
    }
}
