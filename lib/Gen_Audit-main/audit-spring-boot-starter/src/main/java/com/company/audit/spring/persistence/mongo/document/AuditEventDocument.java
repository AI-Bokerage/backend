package com.company.audit.spring.persistence.mongo.document;

import java.time.Instant;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A full, rich copy of an audited event, including its payload — unlike
 * {@code audit_immutable} (Postgres), which deliberately never stores the payload.
 *
 * <p>{@code payloadHash} and {@code eventHash} are stored hex-encoded alongside the rich data,
 * not just for convenience: {@code CanonicalJsonSerializer} lives in {@code audit-core}'s
 * {@code internal} package, so an investigator reading this collection directly (via a Mongo
 * shell or GUI tool) has no way to recompute either hash themselves. Having them stored plainly
 * is the only independent check available in that context.
 *
 * <p>{@code schemaVersion} exists because this collection has no migration tooling the way the
 * SQL side has Flyway; a long-lived Mongo collection without a version marker on each document is
 * a well-known source of pain the first time its shape needs to change.
 *
 * <p>The collection name is bound to the {@code audit.mongo.collection} property (see
 * {@code AuditProperties}) via Spring Data's property-placeholder support for {@code @Document},
 * resolved directly from the {@code Environment} rather than a bean reference — this avoids any
 * bean-creation-order dependency on {@code AuditProperties} itself.
 */
@Document(collection = "${audit.mongo.collection:audit_events}")
public class AuditEventDocument {

    /** The current schema version written by this starter. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    @Id
    private String id;

    @Indexed
    private String partitionKey;

    private long seq;
    private String eventType;
    private String actorType;
    private String actorId;
    private String category;
    private Instant occurredAt;
    private Map<String, Object> payload;
    private String resourceType;
    private String resourceId;
    private String reason;
    private String payloadHash;

    @Indexed
    private String eventHash;

    private int schemaVersion;

    /**
     * Required by Spring Data MongoDB.
     */
    protected AuditEventDocument() {
    }

    /**
     * Creates a fully populated document.
     *
     * @param id the document id, taken from the source event's id
     * @param partitionKey the partition this event belongs to
     * @param seq the sequence number assigned when the event was appended to the ledger
     * @param eventType the event's type discriminator
     * @param actorType the actor type, as its enum name
     * @param actorId the actor identifier
     * @param category the audit category, as its enum name
     * @param occurredAt the instant the audited occurrence took place
     * @param payload the event's full payload
     * @param resourceType the resource type, or {@code null}
     * @param resourceId the resource identifier, or {@code null}
     * @param reason the reason, or {@code null}
     * @param payloadHash the hex-encoded payload hash
     * @param eventHash the hex-encoded event hash
     * @param schemaVersion the schema version this document was written under
     */
    public AuditEventDocument(
            String id,
            String partitionKey,
            long seq,
            String eventType,
            String actorType,
            String actorId,
            String category,
            Instant occurredAt,
            Map<String, Object> payload,
            String resourceType,
            String resourceId,
            String reason,
            String payloadHash,
            String eventHash,
            int schemaVersion) {
        this.id = id;
        this.partitionKey = partitionKey;
        this.seq = seq;
        this.eventType = eventType;
        this.actorType = actorType;
        this.actorId = actorId;
        this.category = category;
        this.occurredAt = occurredAt;
        this.payload = payload;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.reason = reason;
        this.payloadHash = payloadHash;
        this.eventHash = eventHash;
        this.schemaVersion = schemaVersion;
    }

    /**
     * Returns the document id.
     *
     * @return the document id
     */
    public String getId() {
        return id;
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
     * Returns the sequence number assigned in the ledger.
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
     * Returns the event's full payload.
     *
     * @return the payload
     */
    public Map<String, Object> getPayload() {
        return payload;
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
     * Returns the hex-encoded payload hash.
     *
     * @return the payload hash, hex-encoded
     */
    public String getPayloadHash() {
        return payloadHash;
    }

    /**
     * Returns the hex-encoded event hash.
     *
     * @return the event hash, hex-encoded
     */
    public String getEventHash() {
        return eventHash;
    }

    /**
     * Returns the schema version this document was written under.
     *
     * @return the schema version
     */
    public int getSchemaVersion() {
        return schemaVersion;
    }
}
