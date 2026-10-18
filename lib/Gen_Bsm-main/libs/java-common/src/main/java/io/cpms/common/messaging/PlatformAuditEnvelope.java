package io.cpms.common.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

/**
 * Shared Java wire shape for the cross-service audit-exchange pipeline. Field-for-field
 * identical to aud-svc's real listener-side shape ({@code io.cpms.aud.api.dto.request
 * .PlatformEventMessage}, which aud-svc's Rabbit listener actually deserializes) and to Node's
 * {@code EventEnvelope} (`libs/node-common/src/messaging/event-bus.ts`). Every Java producer
 * builds this record and publishes its serialized JSON form, so aud-svc sees byte-identical
 * shape regardless of which service — Java or Node — produced the message.
 *
 * <p>{@code tenantId}/{@code traceId}/{@code correlationId} are legitimately nullable — not
 * every event has a tenant (none in this phase, but future producers may) or a trace/correlation
 * context.
 */
public record PlatformAuditEnvelope(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("event_version") int eventVersion,
        @JsonProperty("occurred_at") Instant occurredAt,
        @JsonProperty("producer") Producer producer,
        @JsonProperty("tenant_id") String tenantId,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("correlation_id") String correlationId,
        @JsonProperty("data") Map<String, Object> data) {

    public record Producer(
            @JsonProperty("service") String service,
            @JsonProperty("version") String version) {
    }
}
