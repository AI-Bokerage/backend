package com.example.authsvc.infrastructure.messaging.outbox;

import com.example.authsvc.config.properties.MessagingProperties;
import com.example.authsvc.infrastructure.persistence.entity.AuthOutboxEventEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthOutboxEventJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Drains {@code auth_outbox_events} to RabbitMQ. Gated the same as the rest of
 * messaging: {@code app.messaging.enabled=true}, and activated by
 * {@code @EnableScheduling} on {@code MessagingConfig}. {@link RabbitTemplate}
 * is optional — no broker configured means {@link #relay()} silently no-ops,
 * matching this codebase's established no-broker-no-crash convention.
 *
 * <p>Retry semantics match CPMS's real implementation exactly: 3 total attempts,
 * no backoff beyond the fixed poll interval, {@code FAILED} on exhaustion with
 * no further automatic retry.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true")
public class AuthOutboxRelayJob {

    private static final int MAX_RETRIES = 3;

    private final AuthOutboxEventJpaRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties messagingProperties;

    @Value("${app.messaging.outbox.batch-size:50}")
    private int batchSize;

    public AuthOutboxRelayJob(
            AuthOutboxEventJpaRepository repository,
            @Autowired(required = false) RabbitTemplate rabbitTemplate,
            MessagingProperties messagingProperties) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.messagingProperties = messagingProperties;
    }

    @Scheduled(fixedDelayString = "${app.messaging.outbox.relay-interval-ms:5000}")
    @Transactional
    public void relay() {
        if (rabbitTemplate == null) {
            return;
        }

        List<AuthOutboxEventEntity> batch = repository.findPendingForUpdate(batchSize);
        for (AuthOutboxEventEntity event : batch) {
            try {
                String exchange = event.getTargetExchange() != null
                        ? event.getTargetExchange()
                        : messagingProperties.getExchange();

                Message message = MessageBuilder
                        .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setMessageId(event.getEventId().toString())
                        .build();

                rabbitTemplate.send(exchange, event.getEventType(), message);
                repository.markPublished(event.getId(), Instant.now());
                log.info("outbox.relay.sent id={} eventType={} exchange={}",
                        event.getId(), event.getEventType(), exchange);
            } catch (Exception e) {
                if (event.getRetryCount() >= MAX_RETRIES - 1) {
                    repository.markFailed(event.getId(), e.getMessage());
                    log.error("outbox.relay.failed id={} eventType={} reason={}",
                            event.getId(), event.getEventType(), e.getMessage());
                } else {
                    repository.incrementRetry(event.getId(), e.getMessage());
                    log.warn("outbox.relay.retry id={} eventType={} retryCount={} reason={}",
                            event.getId(), event.getEventType(), event.getRetryCount() + 1, e.getMessage());
                }
            }
        }
    }
}
