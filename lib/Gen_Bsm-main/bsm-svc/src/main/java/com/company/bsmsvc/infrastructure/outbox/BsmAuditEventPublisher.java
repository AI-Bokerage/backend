package com.company.bsmsvc.infrastructure.outbox;

import com.company.bsmsvc.domain.port.EventPublisherPort;
import io.cpms.common.messaging.PlatformAuditEnvelope;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Builds a {@link PlatformAuditEnvelope}-shaped payload and enqueues it to the same transactional
 * BSM outbox ({@link BsmOutboxService}) every other bsm-svc outbox producer uses — the single
 * shared entry point for every NEW audit-only outbox row added in this phase (invoice.created's
 * audit leg, all 5 dunning audit legs, and the real payment/refund/credit-note lifecycle events).
 *
 * <p>Sets the outbox row's {@code event_type} and {@code routing_key} to the same dotted wire
 * string (unlike {@code BsmSubscriptionEventPublisher}, which uses a separate PascalCase internal
 * name for {@code event_type} — not needed here since these rows exist only to carry that one wire
 * string to {@link BsmAuditEventRouter}). {@link BsmOutboxPublisher} resolves the target
 * exchange(s) from the routing key at dispatch time.
 *
 * <p>{@code tenantId} may be null (not expected in this phase, but the envelope/router tolerate
 * it); {@code actorId} is genuinely absent for several call sites (no actor threaded through those
 * code paths today) — callers pass {@code null} rather than a fabricated value, and the payload
 * carries an explicit {@code null} "actorId" rather than omitting the key.
 */
@Component
@RequiredArgsConstructor
public class BsmAuditEventPublisher implements EventPublisherPort {

    private static final int EVENT_VERSION = 1;
    private static final String PRODUCER_VERSION = "1.0";
    private static final String PRODUCER_SERVICE = "bsm-svc";

    private final BsmOutboxService outboxService;

    public void publish(String eventType, UUID tenantId, String aggregateType, UUID aggregateId,
                         UUID actorId, Map<String, Object> data) {
        Map<String, Object> enrichedData = new java.util.HashMap<>(data);
        // "actor_id"/"actor_type" (snake_case), not "actorId": the shared PlatformEventMapper/
        // TenantAuditEventMapper (aud-svc/audit-svc) read data.get("actor_id") verbatim — the
        // prior camelCase key was a silent contract mismatch, so every bsm-svc audit event ended
        // up attributed to "bsm-svc" (the mapper's producer.service fallback) instead of the real
        // actor, found via a systematic cross-producer audit after the same bug was confirmed in
        // auth-svc (docs/audit/16).
        enrichedData.put("actor_id", actorId != null ? actorId.toString() : null);
        enrichedData.put("actor_type", actorId != null ? "HUMAN" : "SYSTEM");

        PlatformAuditEnvelope envelope = new PlatformAuditEnvelope(
                UUID.randomUUID().toString(),
                eventType,
                EVENT_VERSION,
                Instant.now(),
                new PlatformAuditEnvelope.Producer(PRODUCER_SERVICE, PRODUCER_VERSION),
                tenantId != null ? tenantId.toString() : null,
                null,
                null,
                enrichedData);

        outboxService.save(aggregateType, aggregateId, eventType, eventType, envelope);
    }
}
