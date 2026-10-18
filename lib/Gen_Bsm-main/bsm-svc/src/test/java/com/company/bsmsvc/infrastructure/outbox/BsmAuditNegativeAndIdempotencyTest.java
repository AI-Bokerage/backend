package com.company.bsmsvc.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cpms.common.messaging.PlatformAuditEnvelope;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Negative and idempotency coverage for this phase's audit-publishing path, per the required
 * tests list: malformed payload, missing tenant, missing actor (many call sites legitimately
 * have none), unknown event type, and stable/unique event_id per real publish.
 */
@DisplayName("BSM audit publish — negative cases and idempotency")
class BsmAuditNegativeAndIdempotencyTest {

    private final UUID aggregateId = UUID.randomUUID();

    @Test
    @DisplayName("BsmOutboxService.save() failure (e.g. malformed/non-serializable payload) surfaces, never silently swallowed")
    void malformedPayload_surfacesFailure() throws Exception {
        BsmOutboxJpaRepository repo = mock(BsmOutboxJpaRepository.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        org.mockito.Mockito.when(objectMapper.writeValueAsString(any()))
            .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("bad payload") { });
        BsmOutboxService outboxService = new BsmOutboxService(repo, objectMapper);

        assertThatThrownBy(() -> outboxService.save("Agg", aggregateId, "some.event", "some.event", new Object()))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("missing tenant (null tenantId) — envelope carries a null tenant_id, no crash")
    void missingTenant_handledGracefully() {
        BsmOutboxService outboxService = mock(BsmOutboxService.class);
        BsmAuditEventPublisher publisher = new BsmAuditEventPublisher(outboxService);

        publisher.publish("payment.created", null, "Payment", aggregateId, null, Map.of());

        org.mockito.ArgumentCaptor<Object> captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(outboxService).save(any(), any(), any(), any(), captor.capture());
        PlatformAuditEnvelope envelope = (PlatformAuditEnvelope) captor.getValue();
        assertThat(envelope.tenantId()).isNull();
    }

    @Test
    @DisplayName("missing actor (null actorId) — legitimate for most call sites; envelope carries explicit null, no crash")
    void missingActor_handledGracefully() {
        BsmOutboxService outboxService = mock(BsmOutboxService.class);
        BsmAuditEventPublisher publisher = new BsmAuditEventPublisher(outboxService);

        publisher.publish("credit_note.applied", UUID.randomUUID(), "CreditNote", aggregateId, null, Map.of());

        org.mockito.ArgumentCaptor<Object> captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(outboxService).save(any(), any(), any(), any(), captor.capture());
        PlatformAuditEnvelope envelope = (PlatformAuditEnvelope) captor.getValue();
        assertThat(envelope.data()).containsEntry("actor_id", null);
        assertThat(envelope.data()).containsEntry("actor_type", "SYSTEM");
    }

    @Test
    @DisplayName("unknown event type never crashes the publisher's exchange resolution — falls back to today's default")
    void unknownEventType_noCrash() {
        BsmOutboxPublisher publisher = new BsmOutboxPublisher(
            mock(BsmOutboxJpaRepository.class),
            mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class),
            mock(BsmOutboxConfirmHandler.class),
            new BsmAuditEventRouter(),
            mock(org.springframework.transaction.PlatformTransactionManager.class),
            50);

        var exchanges = publisher.resolveExchanges("totally.unrecognized.event.type");
        assertThat(exchanges).containsExactly(com.company.bsmsvc.messaging.BsmMessagingRouting.EVENTS_EXCHANGE);
    }

    @Test
    @DisplayName("idempotency: every real publish() call gets a fresh, unique event_id — stable dedup key for aud-svc")
    void everyPublish_getsUniqueEventId() {
        BsmOutboxService outboxService = mock(BsmOutboxService.class);
        BsmAuditEventPublisher publisher = new BsmAuditEventPublisher(outboxService);
        UUID tenantId = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            publisher.publish("refund.created", tenantId, "RefundRequest", UUID.randomUUID(), tenantId, Map.of());
        }

        org.mockito.ArgumentCaptor<Object> captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(outboxService, org.mockito.Mockito.times(5)).save(any(), any(), any(), any(), captor.capture());
        var ids = captor.getAllValues().stream()
            .map(p -> ((PlatformAuditEnvelope) p).eventId())
            .distinct()
            .count();
        assertThat(ids).isEqualTo(5);
    }
}
