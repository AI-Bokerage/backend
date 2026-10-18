package com.company.audit.core.api;

import com.company.audit.core.api.enums.ActorType;
import com.company.audit.core.api.enums.AuditCategory;
import java.time.Instant;
import java.util.Map;

/**
 * A fluent builder for {@link AuditEvent}.
 *
 * <p>All validation is deferred to {@link AuditEvent}'s canonical constructor, which is invoked
 * by {@link #build()}.
 */
public final class AuditEventBuilder {

    private String id;
    private String partitionKey;
    private String eventType;
    private ActorType actorType;
    private String actorId;
    private AuditCategory category;
    private Instant occurredAt;
    private Map<String, Object> payload;
    private String resourceType;
    private String resourceId;
    private String reason;

    AuditEventBuilder() {
    }

    /**
     * Sets the event identifier.
     *
     * @param id the caller-assigned identifier for this event
     * @return this builder
     */
    public AuditEventBuilder id(String id) {
        this.id = id;
        return this;
    }

    /**
     * Sets the partition key.
     *
     * @param partitionKey the key identifying which independent hash chain this event belongs to
     * @return this builder
     */
    public AuditEventBuilder partitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
        return this;
    }

    /**
     * Sets the event type.
     *
     * @param eventType a caller-defined type discriminator for this event
     * @return this builder
     */
    public AuditEventBuilder eventType(String eventType) {
        this.eventType = eventType;
        return this;
    }

    /**
     * Sets the actor type.
     *
     * @param actorType the general category of actor that initiated this event
     * @return this builder
     */
    public AuditEventBuilder actorType(ActorType actorType) {
        this.actorType = actorType;
        return this;
    }

    /**
     * Sets the actor identifier.
     *
     * @param actorId the identifier of the actor that initiated this event
     * @return this builder
     */
    public AuditEventBuilder actorId(String actorId) {
        this.actorId = actorId;
        return this;
    }

    /**
     * Sets the audit category.
     *
     * @param category the general subject-matter category of this event
     * @return this builder
     */
    public AuditEventBuilder category(AuditCategory category) {
        this.category = category;
        return this;
    }

    /**
     * Sets the occurrence instant.
     *
     * @param occurredAt the instant at which the audited occurrence took place
     * @return this builder
     */
    public AuditEventBuilder occurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
        return this;
    }

    /**
     * Sets the event payload.
     *
     * @param payload arbitrary structured detail about the event
     * @return this builder
     */
    public AuditEventBuilder payload(Map<String, Object> payload) {
        this.payload = payload;
        return this;
    }

    /**
     * Sets the resource type.
     *
     * @param resourceType the type of resource this event concerns
     * @return this builder
     */
    public AuditEventBuilder resourceType(String resourceType) {
        this.resourceType = resourceType;
        return this;
    }

    /**
     * Sets the resource identifier.
     *
     * @param resourceId the identifier of the resource this event concerns
     * @return this builder
     */
    public AuditEventBuilder resourceId(String resourceId) {
        this.resourceId = resourceId;
        return this;
    }

    /**
     * Sets the reason.
     *
     * @param reason a human-readable explanation for this event
     * @return this builder
     */
    public AuditEventBuilder reason(String reason) {
        this.reason = reason;
        return this;
    }

    /**
     * Builds the {@link AuditEvent}, validating non-nullable fields via the record's canonical
     * constructor.
     *
     * @return a new, immutable {@link AuditEvent}
     */
    public AuditEvent build() {
        return new AuditEvent(
                id, partitionKey, eventType, actorType, actorId, category, occurredAt, payload,
                resourceType, resourceId, reason);
    }
}
