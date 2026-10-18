package com.company.bsmsvc.infrastructure.outbox;

import com.company.bsmsvc.messaging.BsmMessagingRouting;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Polls the {@code bsm_outbox_events} table and dispatches pending events to RabbitMQ
 * using broker-confirm-aware publishing. Modelled on TNT-SVC's OutboxEventPublisher.
 *
 * <p>Crash-safe: IN_FLIGHT rows are reset to PENDING on startup so events whose
 * confirm callbacks were never received are retried automatically.</p>
 */
@Slf4j
@Component
public class BsmOutboxPublisher {

    private final BsmOutboxJpaRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final BsmOutboxConfirmHandler confirmHandler;
    private final BsmAuditEventRouter auditEventRouter;
    private final TransactionTemplate txTemplate;
    private final int batchSize;

    public BsmOutboxPublisher(BsmOutboxJpaRepository repository,
                               RabbitTemplate rabbitTemplate,
                               BsmOutboxConfirmHandler confirmHandler,
                               BsmAuditEventRouter auditEventRouter,
                               org.springframework.transaction.PlatformTransactionManager txManager,
                               @Value("${bsm.outbox.batch-size:50}") int batchSize) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.confirmHandler = confirmHandler;
        this.auditEventRouter = auditEventRouter;
        this.txTemplate = new TransactionTemplate(txManager);
        this.batchSize = batchSize;
    }

    @PostConstruct
    public void recoverInFlight() {
        int reset = repository.resetInFlightToPending();
        if (reset > 0) {
            log.warn("[BSM-OUTBOX] Startup recovery: reset {} IN_FLIGHT → PENDING", reset);
        }
    }

    public int publishPending() {
        List<BsmOutboxEventEntity> batch = txTemplate.execute(s -> {
            // FOR UPDATE SKIP LOCKED ensures each pod in a multi-node deployment claims a
            // disjoint set of outbox rows.  Rows already locked by another transaction are
            // skipped instead of blocked, so two pods never dispatch the same event to
            // RabbitMQ simultaneously.  The @Version on the entity provides the OLE backstop
            // if the SKIP LOCKED query is not supported by the driver.
            List<BsmOutboxEventEntity> pending = repository.findPendingSkipLocked(batchSize);
            if (pending.isEmpty()) return pending;
            pending.forEach(e -> {
                e.setStatus(BsmOutboxEventStatus.IN_FLIGHT);
                repository.save(e);
            });
            return pending;
        });

        if (batch == null || batch.isEmpty()) return 0;

        int dispatched = 0;
        for (BsmOutboxEventEntity event : batch) {
            UUID eventId = event.getId();

            // Resolve the exchange list for this row: BsmAuditEventRouter is consulted first — if
            // it classifies this routing key, its audit exchange(s) are used, plus the existing
            // CPMS_EVENTS_EXCHANGE default IF-AND-ONLY-IF Routing#alsoDefault() says so (true only
            // for the five pre-existing subscription events, preserving their business publish
            // unchanged). Anything unclassified falls through to today's original single-exchange
            // default — zero behavior change for event types outside this phase's scope.
            List<String> exchanges = resolveExchanges(event.getRoutingKey());

            // Primary exchange (index 0) keeps the exact original broker-confirm-aware publish —
            // CorrelationData, confirm callback, retry/DLQ handling via BsmOutboxConfirmHandler —
            // completely unchanged in shape. Any additional exchange (only ever the audit exchange,
            // only for dual-publish subscription rows) is a best-effort, independently-attempted
            // secondary send: its failure is logged but never blocks or reverses the primary
            // confirm outcome.
            // ponytail: secondary sends have no confirm-tracked retry of their own — upgrade to a
            // dedicated confirm/DLQ path per secondary exchange if audit-leg delivery guarantees
            // need to match the primary's at-least-once semantics exactly.
            String primaryExchange = exchanges.get(0);
            CorrelationData cd = new CorrelationData(eventId.toString());
            cd.getFuture().whenComplete((confirm, ex) ->
                confirmHandler.handleConfirm(eventId, event.getRoutingKey(), confirm, ex));
            try {
                // Build a raw Message from bytes to avoid Jackson2JsonMessageConverter
                // double-encoding the String payload as a JSON string literal.
                Message rawMessage = MessageBuilder
                    .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setHeader("X-Source-Service", "bsm-svc")
                    .build();
                rabbitTemplate.convertAndSend(
                    primaryExchange,
                    event.getRoutingKey(),
                    rawMessage,
                    cd);
                dispatched++;
            } catch (RuntimeException e) {
                log.error("[BSM-OUTBOX] Dispatch failed: eventId={} exchange={} routingKey={}: {}",
                    eventId, primaryExchange, event.getRoutingKey(), e.getMessage());
                confirmHandler.handleConfirm(eventId, event.getRoutingKey(), null, e);
            }

            for (int i = 1; i < exchanges.size(); i++) {
                String secondaryExchange = exchanges.get(i);
                try {
                    Message rawMessage = MessageBuilder
                        .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setHeader("X-Source-Service", "bsm-svc")
                        .build();
                    rabbitTemplate.convertAndSend(secondaryExchange, event.getRoutingKey(), rawMessage);
                    log.debug("[BSM-OUTBOX] Secondary audit publish sent eventId={} exchange={} routingKey={}",
                        eventId, secondaryExchange, event.getRoutingKey());
                } catch (RuntimeException e) {
                    log.error("[BSM-OUTBOX] Secondary audit publish failed eventId={} exchange={} routingKey={}: {}",
                        eventId, secondaryExchange, event.getRoutingKey(), e.getMessage());
                }
            }
        }
        if (dispatched > 0) log.info("[BSM-OUTBOX] Dispatched {} events", dispatched);
        return dispatched;
    }

    /**
     * Resolves the ordered exchange list for a given outbox routing key. Package-visible (rather
     * than private) so {@code BsmBusinessPublisherRegressionTest} /
     * {@code BsmAuditRoutingTest}-adjacent unit tests can assert routing decisions directly,
     * without needing to drive the whole transactional batch-fetch + RabbitMQ dispatch flow.
     */
    List<String> resolveExchanges(String routingKey) {
        Optional<BsmAuditEventRouter.Routing> routing = auditEventRouter.resolve(routingKey);
        List<String> exchanges = new ArrayList<>();
        if (routing.isPresent()) {
            if (routing.get().alsoDefault()) {
                exchanges.add(BsmMessagingRouting.EVENTS_EXCHANGE);
            }
            exchanges.addAll(routing.get().exchanges());
        } else {
            exchanges.add(BsmMessagingRouting.EVENTS_EXCHANGE);
        }
        return exchanges;
    }
}
