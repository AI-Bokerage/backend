package com.company.bsmsvc.infrastructure.outbox;

import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles RabbitMQ broker-confirm callbacks for the BSM transactional outbox.
 * Modelled on TNT-SVC's OutboxConfirmHandler.
 */
@Slf4j
@Component
public class BsmOutboxConfirmHandler {

    static final int MAX_RETRIES = 3;

    private final BsmOutboxJpaRepository repository;
    private final TransactionTemplate txTemplate;
    private final RabbitTemplate rabbitTemplate;

    public BsmOutboxConfirmHandler(BsmOutboxJpaRepository repository,
                                    org.springframework.transaction.PlatformTransactionManager txManager,
                                    RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.txTemplate = new TransactionTemplate(txManager);
        this.rabbitTemplate = rabbitTemplate;
    }

    public void handleConfirm(UUID eventId, String routingKey,
                               CorrelationData.Confirm confirm, Throwable ex) {
        if (ex != null) {
            onNack(eventId, routingKey, "exception: " + ex.getMessage());
            return;
        }
        if (confirm == null) {
            onNack(eventId, routingKey, "null confirm — channel closed?");
            return;
        }
        if (confirm.isAck()) {
            onAck(eventId);
        } else {
            onNack(eventId, routingKey, confirm.getReason());
        }
    }

    private void onAck(UUID eventId) {
        txTemplate.execute(s -> {
            repository.findById(eventId).ifPresent(e -> {
                e.setStatus(BsmOutboxEventStatus.PUBLISHED);
                e.setPublishedAt(Instant.now());
                repository.save(e);
            });
            return null;
        });
    }

    private void onNack(UUID eventId, String routingKey, String reason) {
        txTemplate.execute(s -> {
            repository.findById(eventId).ifPresent(e -> {
                int retry = e.getRetryCount() + 1;
                e.setRetryCount(retry);
                if (retry >= MAX_RETRIES) {
                    e.setStatus(BsmOutboxEventStatus.FAILED);
                    repository.save(e);
                    log.error("[BSM-OUTBOX] Max retries reached FAILED: eventId={} routingKey={} reason={}",
                        eventId, routingKey, reason);
                    routeToDlq(e);
                } else {
                    e.setStatus(BsmOutboxEventStatus.PENDING);
                    repository.save(e);
                    log.warn("[BSM-OUTBOX] NACK reset to PENDING: eventId={} retry={} reason={}",
                        eventId, retry, reason);
                }
            });
            return null;
        });
    }

    private void routeToDlq(BsmOutboxEventEntity event) {
        try {
            String dlqKey = "dlq.bsm.outbox." + event.getEventType().replace(".", "-");
            rabbitTemplate.convertAndSend("cpms.events.dlx", dlqKey, event.getPayload(),
                m -> {
                    m.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
                    m.getMessageProperties().setHeader("x-original-routing-key", event.getRoutingKey());
                    m.getMessageProperties().setHeader("x-outbox-id", event.getId().toString());
                    return m;
                });
        } catch (Exception e) {
            log.error("[BSM-OUTBOX] DLQ routing failed: eventId={}", event.getId(), e);
        }
    }
}
