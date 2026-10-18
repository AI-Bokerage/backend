package com.company.bsmsvc.messaging;

import com.company.bsmsvc.config.MessagingProperties;
import com.company.bsmsvc.domain.enums.DunningStatus;
import com.company.bsmsvc.domain.port.DunningEventPublisher;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitDunningEventPublisher implements DunningEventPublisher {

    private static final String KEY_STARTED   = "bsm.dunning.started";
    private static final String KEY_RETRY     = "bsm.dunning.retry";
    private static final String KEY_RECOVERED = "bsm.dunning.recovered";
    private static final String KEY_SUSPENDED = "bsm.dunning.suspended";
    private static final String KEY_CANCELLED = "bsm.dunning.cancelled";

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties messagingProperties;

    @Override
    public void publishStarted(UUID subscriptionId, UUID tenantId, UUID invoiceId, int attemptNumber) {
        publish(KEY_STARTED, Map.of(
            "subscriptionId", subscriptionId.toString(), "tenantId", tenantId.toString(),
            "invoiceId", invoiceId.toString(), "attemptNumber", attemptNumber,
            "occurredAt", Instant.now().toString()
        ));
    }

    @Override
    public void publishRetry(UUID subscriptionId, UUID tenantId, int attemptNumber, DunningStatus newStatus) {
        publish(KEY_RETRY, Map.of(
            "subscriptionId", subscriptionId.toString(), "tenantId", tenantId.toString(),
            "attemptNumber", attemptNumber, "newStatus", newStatus.name(),
            "occurredAt", Instant.now().toString()
        ));
    }

    @Override
    public void publishRecovered(UUID subscriptionId, UUID tenantId, UUID invoiceId) {
        publish(KEY_RECOVERED, Map.of(
            "subscriptionId", subscriptionId.toString(), "tenantId", tenantId.toString(),
            "invoiceId", invoiceId != null ? invoiceId.toString() : "",
            "occurredAt", Instant.now().toString()
        ));
    }

    @Override
    public void publishSuspended(UUID subscriptionId, UUID tenantId) {
        publish(KEY_SUSPENDED, Map.of(
            "subscriptionId", subscriptionId.toString(), "tenantId", tenantId.toString(),
            "occurredAt", Instant.now().toString()
        ));
    }

    @Override
    public void publishCancelled(UUID subscriptionId, UUID tenantId) {
        publish(KEY_CANCELLED, Map.of(
            "subscriptionId", subscriptionId.toString(), "tenantId", tenantId.toString(),
            "occurredAt", Instant.now().toString()
        ));
    }

    private void publish(String routingKey, Object payload) {
        String exchange = messagingProperties.getEventsExchange();
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, payload);
            log.info("[DunningEvent] exchange={} routingKey={}", exchange, routingKey);
        } catch (Exception e) {
            log.error("[DunningEvent] Failed to publish routingKey={}: {}", routingKey, e.getMessage());
        }
    }
}
