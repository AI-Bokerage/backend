package com.company.audit.spring.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mirroring the {@code audit_immutable} table 1:1.
 *
 * <p>This entity never crosses the adapter boundary: only
 * {@link com.company.audit.core.api.ChainedRecord} is exposed outside {@code persistence.jpa}.
 * The raw event payload is deliberately not a column here — this table proves chain integrity,
 * it is not a way to read back what an event contained.
 */
@Entity
@Table(name = "audit_immutable")
public class AuditImmutableEntity {

    @Id
    @Column(name = "audit_id")
    private UUID auditId;

    @Column(name = "partition_key", nullable = false)
    private String partitionKey;

    @Column(name = "seq", nullable = false)
    private long seq;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "actor_type", nullable = false)
    private String actorType;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "reason")
    private String reason;

    @Column(name = "payload_hash", nullable = false)
    private byte[] payloadHash;

    @Column(name = "prev_event_hash", nullable = false)
    private byte[] prevEventHash;

    @Column(name = "event_hash", nullable = false)
    private byte[] eventHash;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    /**
     * Required by JPA.
     */
    protected AuditImmutableEntity() {
    }

    /**
     * Creates a fully populated entity.
     *
     * @param auditId the primary key, parsed from the source event's id
     * @param partitionKey the partition this record belongs to
     * @param seq the sequence number within the partition
     * @param eventType the event's type discriminator
     * @param actorType the actor type, as its enum name
     * @param actorId the actor identifier
     * @param category the audit category, as its enum name
     * @param occurredAt the instant the audited occurrence took place
     * @param resourceType the resource type, or {@code null}
     * @param resourceId the resource identifier, or {@code null}
     * @param reason the reason, or {@code null}
     * @param payloadHash the payload hash bytes
     * @param prevEventHash the previous event hash bytes
     * @param eventHash the event hash bytes
     * @param recordedAt the instant this record was persisted
     */
    public AuditImmutableEntity(
            UUID auditId,
            String partitionKey,
            long seq,
            String eventType,
            String actorType,
            String actorId,
            String category,
            Instant occurredAt,
            String resourceType,
            String resourceId,
            String reason,
            byte[] payloadHash,
            byte[] prevEventHash,
            byte[] eventHash,
            Instant recordedAt) {
        this.auditId = auditId;
        this.partitionKey = partitionKey;
        this.seq = seq;
        this.eventType = eventType;
        this.actorType = actorType;
        this.actorId = actorId;
        this.category = category;
        this.occurredAt = occurredAt;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.reason = reason;
        this.payloadHash = payloadHash;
        this.prevEventHash = prevEventHash;
        this.eventHash = eventHash;
        this.recordedAt = recordedAt;
    }

    /**
     * Returns the primary key.
     *
     * @return the audit id
     */
    public UUID getAuditId() {
        return auditId;
    }

    /**
     * Returns the partition key.
     *
     * @return the partition key
     */
    public String getPartitionKey() {
        return partitionKey;
    }

    /**
     * Returns the sequence number within the partition.
     *
     * @return the sequence number
     */
    public long getSeq() {
        return seq;
    }

    /**
     * Returns the event type.
     *
     * @return the event type
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * Returns the actor type's enum name.
     *
     * @return the actor type
     */
    public String getActorType() {
        return actorType;
    }

    /**
     * Returns the actor identifier.
     *
     * @return the actor id
     */
    public String getActorId() {
        return actorId;
    }

    /**
     * Returns the audit category's enum name.
     *
     * @return the category
     */
    public String getCategory() {
        return category;
    }

    /**
     * Returns the instant the audited occurrence took place.
     *
     * @return the occurrence instant
     */
    public Instant getOccurredAt() {
        return occurredAt;
    }

    /**
     * Returns the resource type, if any.
     *
     * @return the resource type, or {@code null}
     */
    public String getResourceType() {
        return resourceType;
    }

    /**
     * Returns the resource identifier, if any.
     *
     * @return the resource id, or {@code null}
     */
    public String getResourceId() {
        return resourceId;
    }

    /**
     * Returns the reason, if any.
     *
     * @return the reason, or {@code null}
     */
    public String getReason() {
        return reason;
    }

    /**
     * Returns the payload hash bytes.
     *
     * @return the payload hash bytes
     */
    public byte[] getPayloadHash() {
        return payloadHash;
    }

    /**
     * Returns the previous event hash bytes.
     *
     * @return the previous event hash bytes
     */
    public byte[] getPrevEventHash() {
        return prevEventHash;
    }

    /**
     * Returns the event hash bytes.
     *
     * @return the event hash bytes
     */
    public byte[] getEventHash() {
        return eventHash;
    }

    /**
     * Returns the instant this record was persisted.
     *
     * @return the recorded-at instant
     */
    public Instant getRecordedAt() {
        return recordedAt;
    }
}
