package com.company.bsmsvc.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.cpms.common.messaging.PlatformAuditEnvelope;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Every newly-introduced audit event in this phase (invoice.created's audit leg, all 5 dunning
 * audit legs, and the real payment/refund/credit-note events) must enter the outbox correctly
 * shaped as a {@link PlatformAuditEnvelope}, via {@link BsmAuditEventPublisher}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BsmAuditEventPublisher — enqueues PlatformAuditEnvelope-shaped payloads to the outbox")
class BsmAuditOutboxTest {

    static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID AGGREGATE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock BsmOutboxService outboxService;
    @InjectMocks BsmAuditEventPublisher publisher;

    static Stream<String> allNewAuditEventTypes() {
        return Stream.of(
            "bsm.invoice.created",
            "bsm.dunning.started", "bsm.dunning.retry", "bsm.dunning.recovered",
            "bsm.dunning.suspended", "bsm.dunning.cancelled",
            "payment.created", "payment.captured", "payment.failed",
            "refund.created", "refund.completed", "refund.failed",
            "credit_note.created", "credit_note.applied", "credit_note.voided"
        );
    }

    @ParameterizedTest(name = "{0} enters the outbox as a PlatformAuditEnvelope")
    @MethodSource("allNewAuditEventTypes")
    void everyNewAuditEvent_entersOutboxAsEnvelope(String eventType) {
        publisher.publish(eventType, TENANT_ID, "SomeAggregate", AGGREGATE_ID, ACTOR_ID,
            Map.of("k", "v"));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).save(eq("SomeAggregate"), eq(AGGREGATE_ID), eq(eventType), eq(eventType),
            payloadCaptor.capture());

        PlatformAuditEnvelope envelope = (PlatformAuditEnvelope) payloadCaptor.getValue();
        assertThat(envelope.eventType()).isEqualTo(eventType);
        assertThat(envelope.eventId()).isNotBlank();
        assertThat(UUID.fromString(envelope.eventId())).isNotNull();
        assertThat(envelope.tenantId()).isEqualTo(TENANT_ID.toString());
        assertThat(envelope.occurredAt()).isNotNull();
        assertThat(envelope.producer().service()).isEqualTo("bsm-svc");
        assertThat(envelope.data()).containsEntry("k", "v");
        assertThat(envelope.data()).containsEntry("actor_id", ACTOR_ID.toString());
        assertThat(envelope.data()).containsEntry("actor_type", "HUMAN");
    }

    @Test
    @DisplayName("every publish() gets a fresh, unique event_id — idempotency-key stability per real publish")
    void eachPublish_getsUniqueEventId() {
        publisher.publish("bsm.invoice.created", TENANT_ID, "Invoice", AGGREGATE_ID, null, Map.of());
        publisher.publish("bsm.invoice.created", TENANT_ID, "Invoice", AGGREGATE_ID, null, Map.of());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService, org.mockito.Mockito.times(2))
            .save(any(), any(), any(), any(), captor.capture());

        var envelopes = captor.getAllValues();
        String id1 = ((PlatformAuditEnvelope) envelopes.get(0)).eventId();
        String id2 = ((PlatformAuditEnvelope) envelopes.get(1)).eventId();
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("null actorId produces an explicit null actor_id field and actor_type=SYSTEM — never fabricated, never crashes")
    void nullActor_handledGracefully() {
        publisher.publish("credit_note.applied", TENANT_ID, "CreditNote", AGGREGATE_ID, null, Map.of("x", 1));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).save(any(), any(), any(), any(), payloadCaptor.capture());
        PlatformAuditEnvelope envelope = (PlatformAuditEnvelope) payloadCaptor.getValue();
        assertThat(envelope.data()).containsEntry("actor_id", null);
        assertThat(envelope.data()).containsEntry("actor_type", "SYSTEM");
    }

    @Test
    @DisplayName("null tenantId is tolerated — envelope carries a null tenant_id rather than crashing")
    void nullTenant_handledGracefully() {
        publisher.publish("payment.created", null, "Payment", AGGREGATE_ID, null, Map.of());

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).save(any(), any(), any(), any(), payloadCaptor.capture());
        PlatformAuditEnvelope envelope = (PlatformAuditEnvelope) payloadCaptor.getValue();
        assertThat(envelope.tenantId()).isNull();
    }
}
