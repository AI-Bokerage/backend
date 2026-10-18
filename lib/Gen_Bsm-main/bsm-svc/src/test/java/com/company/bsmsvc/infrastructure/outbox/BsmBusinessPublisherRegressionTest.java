package com.company.bsmsvc.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.company.bsmsvc.messaging.BsmMessagingRouting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Proves the "add never replace" guarantee: the five pre-existing subscription-event business
 * publishes keep dispatching to {@code cpms.events} (as the FIRST/primary exchange, which retains
 * the exact original CorrelationData + confirm-handler + retry/DLQ treatment) exactly as they did
 * before this phase — the router only ever ADDS an independent, secondary {@code cpms.audit}
 * publish alongside it. Any event type outside this phase's scope resolves to the single-exchange
 * {@code cpms.events} default, unchanged.
 */
@DisplayName("BsmOutboxPublisher — business-exchange publish is preserved unchanged")
class BsmBusinessPublisherRegressionTest {

    private final BsmOutboxPublisher publisher = new BsmOutboxPublisher(
        mock(BsmOutboxJpaRepository.class),
        mock(RabbitTemplate.class),
        mock(BsmOutboxConfirmHandler.class),
        new BsmAuditEventRouter(),
        mock(PlatformTransactionManager.class),
        50);

    @Test
    @DisplayName("subscription events: cpms.events remains the PRIMARY (first) exchange, cpms.audit is additive only")
    void subscriptionEvents_cpmsEventsStillPrimary() {
        for (String eventType : new String[] {
            BsmMessagingRouting.BSM_SUBSCRIPTION_CREATED,
            BsmMessagingRouting.BSM_SUBSCRIPTION_CHANGED,
            BsmMessagingRouting.BSM_SUBSCRIPTION_CANCELED,
            BsmMessagingRouting.BSM_SUBSCRIPTION_EXPIRED,
            BsmMessagingRouting.BSM_SUBSCRIPTION_RENEWED,
        }) {
            var exchanges = publisher.resolveExchanges(eventType);
            assertThat(exchanges).as(eventType).hasSize(2);
            assertThat(exchanges.get(0)).as(eventType + " primary exchange")
                .isEqualTo(BsmMessagingRouting.EVENTS_EXCHANGE);
            assertThat(exchanges.get(1)).as(eventType + " secondary exchange")
                .isEqualTo(io.cpms.common.messaging.AuditMessagingTopology.AUDIT_EXCHANGE);
        }
    }

    @Test
    @DisplayName("new audit-only rows (invoice/dunning/payment/refund/credit_note) publish ONLY to cpms.audit — never invented onto cpms.events")
    void auditOnlyRows_neverTouchCpmsEvents() {
        for (String eventType : new String[] {
            "bsm.invoice.created", "bsm.dunning.started", "bsm.dunning.retry",
            "bsm.dunning.recovered", "bsm.dunning.suspended", "bsm.dunning.cancelled",
            "payment.created", "payment.captured", "payment.failed",
            "refund.created", "refund.completed", "refund.failed",
            "credit_note.created", "credit_note.applied", "credit_note.voided",
        }) {
            var exchanges = publisher.resolveExchanges(eventType);
            assertThat(exchanges).as(eventType).containsExactly(
                io.cpms.common.messaging.AuditMessagingTopology.AUDIT_EXCHANGE);
        }
    }

    @Test
    @DisplayName("unrouted / unknown event types keep today's original single-exchange default unchanged")
    void unroutedEventTypes_keepOriginalDefault() {
        for (String eventType : new String[] { "some.future.event", "notification.fanout.x" }) {
            var exchanges = publisher.resolveExchanges(eventType);
            assertThat(exchanges).as(eventType).containsExactly(BsmMessagingRouting.EVENTS_EXCHANGE);
        }
    }
}
