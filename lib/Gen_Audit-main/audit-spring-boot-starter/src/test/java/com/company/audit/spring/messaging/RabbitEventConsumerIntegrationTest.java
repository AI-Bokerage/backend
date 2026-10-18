package com.company.audit.spring.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.company.audit.core.api.PartitionContext;
import com.company.audit.core.api.VerificationResult;
import com.company.audit.core.api.enums.AuditChainStatus;
import com.company.audit.spring.partition.PartitionRegistry;
import com.company.audit.spring.port.EventStore;
import com.company.audit.spring.support.AbstractFullStackIntegrationTest;
import com.company.audit.spring.support.TestApplication;
import com.company.audit.spring.wire.AuditEventEnvelope;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/**
 * Publishes real envelopes through a Testcontainers RabbitMQ broker and confirms
 * {@link RabbitEventConsumer} lands valid events in both Postgres and Mongo (via
 * {@link com.company.audit.spring.ingestion.AuditRecorder}), and that a malformed envelope is
 * nacked and requeued rather than silently dropped.
 */
@SpringBootTest(classes = TestApplication.class)
@Import(RabbitEventConsumerIntegrationTest.QueueConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RabbitEventConsumerIntegrationTest extends AbstractFullStackIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private com.company.audit.core.api.AuditVerifier auditVerifier;

    @Autowired
    private PartitionRegistry partitionRegistry;

    @Autowired
    private Environment environment;

    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;

    @Test
    @Order(1)
    void validEnvelopePublishedToQueueIsRecordedInBothStores() {
        String partitionKey = "rabbit-consumer-test-" + UUID.randomUUID();
        AuditEventEnvelope envelope = new AuditEventEnvelope(
                AuditEventEnvelope.CURRENT_SCHEMA_VERSION,
                partitionKey,
                "RABBIT_TEST_EVENT",
                "SERVICE",
                "actor-1",
                "DATA_MUTATION",
                Instant.parse("2024-06-01T12:00:00Z"),
                Map.of("via", "rabbit"),
                null,
                null,
                null,
                Map.of());

        rabbitTemplate.convertAndSend(queueName(), envelope);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(eventStore.findByPartitionKey(partitionKey)).hasSize(1));

        assertThat(eventStore.findByPartitionKey(partitionKey).get(0).payload()).isEqualTo(Map.of("via", "rabbit"));

        PartitionContext context = partitionRegistry.resolve(partitionKey);
        VerificationResult result = auditVerifier.verify(partitionKey, context);
        assertThat(result.status()).isEqualTo(AuditChainStatus.OK);
    }

    /**
     * Regression test for the Phase 9 fix this class's {@code convert} method documents:
     * {@code rabbitTemplate.convertAndSend} (used by every other test here) always sets Spring's
     * own {@code __TypeId__} header, so those tests alone cannot catch a regression back to
     * {@code MessageConverter.fromMessage(Message)} — the exact bug Phase 9 found and fixed. This
     * publishes a raw {@link Message} with a plain JSON body and no {@code __TypeId__} header at
     * all, exactly what a genuine non-Spring producer would send, found missing by a
     * release-readiness audit rather than assumed covered by the tests already here.
     */
    @Test
    @Order(0)
    void envelopeWithNoTypeIdHeaderFromANonSpringProducerIsStillRecorded() {
        String partitionKey = "rabbit-consumer-test-no-typeid-" + UUID.randomUUID();
        String json = "{"
                + "\"schemaVersion\":" + AuditEventEnvelope.CURRENT_SCHEMA_VERSION + ","
                + "\"partitionKey\":\"" + partitionKey + "\","
                + "\"eventType\":\"RABBIT_TEST_EVENT\","
                + "\"actorType\":\"SERVICE\","
                + "\"actorId\":\"actor-1\","
                + "\"category\":\"DATA_MUTATION\","
                + "\"occurredAt\":\"2024-06-01T12:00:00Z\","
                + "\"payload\":{\"via\":\"non-spring-producer\"},"
                + "\"resourceType\":null,"
                + "\"resourceId\":null,"
                + "\"reason\":null,"
                + "\"headers\":{}"
                + "}";

        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        Message rawMessage = new Message(json.getBytes(StandardCharsets.UTF_8), properties);

        rabbitTemplate.send(queueName(), rawMessage);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(eventStore.findByPartitionKey(partitionKey)).hasSize(1));

        assertThat(eventStore.findByPartitionKey(partitionKey).get(0).payload())
                .isEqualTo(Map.of("via", "non-spring-producer"));
    }

    @Test
    @Order(2)
    void malformedEnvelopeIsNackedAndRequeuedNotSilentlyDropped() {
        String partitionKey = "rabbit-consumer-test-malformed-" + UUID.randomUUID();
        AuditEventEnvelope malformed = new AuditEventEnvelope(
                AuditEventEnvelope.CURRENT_SCHEMA_VERSION,
                partitionKey,
                "RABBIT_TEST_EVENT",
                "NOT_A_REAL_ACTOR_TYPE",
                "actor-1",
                "DATA_MUTATION",
                Instant.parse("2024-06-01T12:00:00Z"),
                Map.of(),
                null,
                null,
                null,
                Map.of());

        rabbitTemplate.convertAndSend(queueName(), malformed);

        // Give the listener a few redelivery attempts (each one logs the mapper's specific
        // UnrecognizedEnvelopeValueException as the cause of the listener failure), then pause
        // the listener container: while it's running, the message oscillates between "ready"
        // and "unacked" (in-flight, being retried) and a message-count snapshot can catch it
        // mid-flight and read 0 even though nothing was lost. Stopping the container forces any
        // in-flight message back to "ready", making the queue depth check deterministic.
        try {
            Thread.sleep(Duration.ofSeconds(3));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        listenerRegistry.stop();

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(amqpAdmin.getQueueInfo(queueName()).getMessageCount())
                        .isGreaterThanOrEqualTo(1));

        assertThat(eventStore.findByPartitionKey(partitionKey)).isEmpty();

        // Clean up so this deliberately-poisoned message doesn't linger and interfere with
        // anything else sharing this broker/queue for the remainder of the test run.
        amqpAdmin.purgeQueue(queueName());
        listenerRegistry.start();
    }

    private String queueName() {
        return environment.getProperty("audit.rabbit.queue", "audit.events");
    }

    @Configuration
    static class QueueConfig {

        @Bean
        Queue auditEventsQueue() {
            return new Queue("audit.events", true);
        }
    }
}
