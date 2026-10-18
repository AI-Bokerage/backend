package com.company.audit.spring.persistence.mongo.mapper;

import com.company.audit.core.api.AuditEvent;
import com.company.audit.core.api.AuditEventBuilder;
import com.company.audit.core.api.ChainedRecord;
import com.company.audit.core.api.enums.ActorType;
import com.company.audit.core.api.enums.AuditCategory;
import com.company.audit.spring.persistence.mongo.document.AuditEventDocument;

/**
 * Converts between {@link AuditEvent} and {@link AuditEventDocument}.
 *
 * <p>Unlike the Postgres mapper ({@code AuditImmutableMapper}), the {@link #toDomain} direction
 * here reconstructs a full {@link AuditEvent} with its real payload — this is the one place in
 * the starter where the payload actually exists to read back.
 */
public class AuditEventMongoMapper {

    /**
     * Creates a new mapper.
     */
    public AuditEventMongoMapper() {
    }

    /**
     * Converts an event and its resulting chained record into a persistable document.
     *
     * @param event the full audit event, including its payload
     * @param record the chained record produced by appending {@code event} to the ledger
     * @return the equivalent document
     */
    public AuditEventDocument toDocument(AuditEvent event, ChainedRecord record) {
        return new AuditEventDocument(
                event.id(),
                event.partitionKey(),
                record.seq(),
                event.eventType(),
                event.actorType().name(),
                event.actorId(),
                event.category().name(),
                event.occurredAt(),
                event.payload(),
                event.resourceType(),
                event.resourceId(),
                event.reason(),
                record.payloadHash().hex(),
                record.eventHash().hex(),
                AuditEventDocument.CURRENT_SCHEMA_VERSION);
    }

    /**
     * Converts a persisted document back into its domain form, payload included.
     *
     * @param document the document to convert
     * @return the equivalent audit event
     */
    public AuditEvent toDomain(AuditEventDocument document) {
        return AuditEvent.builder()
                .id(document.getId())
                .partitionKey(document.getPartitionKey())
                .eventType(document.getEventType())
                .actorType(ActorType.valueOf(document.getActorType()))
                .actorId(document.getActorId())
                .category(AuditCategory.valueOf(document.getCategory()))
                .occurredAt(document.getOccurredAt())
                .payload(document.getPayload())
                .resourceType(document.getResourceType())
                .resourceId(document.getResourceId())
                .reason(document.getReason())
                .build();
    }
}
