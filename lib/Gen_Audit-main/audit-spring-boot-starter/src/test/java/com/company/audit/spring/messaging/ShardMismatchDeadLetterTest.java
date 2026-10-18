package com.company.audit.spring.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.company.audit.spring.sharding.ConsistentHashPartitionShardResolver;
import com.company.audit.spring.support.AbstractFullStackIntegrationTest;
import com.company.audit.spring.support.TestApplication;
import com.company.audit.spring.wire.AuditEventEnvelope;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Deliberately forces repeated shard-mismatch rejections for the same message — a single
 * instance configured with an impossible topology (it never owns the one partition under test) —
 * and confirms the hazard {@link RabbitEventConsumer#MAX_SHARD_MISMATCH_REDELIVERIES} exists to
 * close: the rejection loop actually stops once the bound is crossed, with the message landing on
 * the dead-letter queue and an {@code ERROR} log line, rather than continuing to reject the same
 * message forever.
 */
class ShardMismatchDeadLetterTest extends AbstractFullStackIntegrationTest {

    private static final int TOTAL_INSTANCES = 2;
    private static final String QUEUE_NAME = "dead-letter-test-queue-" + UUID.randomUUID();
    private static final String DEAD_LETTER_QUEUE_NAME = QUEUE_NAME + ".dead-letter";

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger consumerLogger;

    @AfterEach
    void detachAppender() {
        if (consumerLogger != null) {
            consumerLogger.detachAppender(appender);
        }
    }

    @Test
    void repeatedShardMismatchStopsLoopingAndLandsOnDeadLetterQueueWithErrorLog() throws Exception {
        String partitionKey = findPartitionKeyNotOwnedByInstance(0);

        ConfigurableApplicationContext instance = buildInstance(0);
        try {
            // Attached only after the context has finished starting: Spring Boot's own logging
            // initialization resets/reconfigures logback as part of starting a new
            // SpringApplication, which would silently detach an appender registered beforehand.
            consumerLogger = (Logger) LoggerFactory.getLogger(RabbitEventConsumer.class);
            appender.start();
            consumerLogger.addAppender(appender);

            RabbitTemplate rabbitTemplate = instance.getBean(RabbitTemplate.class);
            rabbitTemplate.convertAndSend(QUEUE_NAME, buildEnvelope(partitionKey));

            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                Object deadLettered = rabbitTemplate.receiveAndConvert(DEAD_LETTER_QUEUE_NAME);
                assertThat(deadLettered).isNotNull();
            });

            // The loop actually stopped: nothing left circulating on the main queue for this
            // message, and no second copy ever reached the dead-letter queue either.
            await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                var mainQueueInfo = instance.getBean(org.springframework.amqp.core.AmqpAdmin.class)
                        .getQueueInfo(QUEUE_NAME);
                assertThat(mainQueueInfo.getMessageCount()).isZero();
            });
            assertThat(rabbitTemplate.receiveAndConvert(DEAD_LETTER_QUEUE_NAME)).isNull();

            boolean errorLogged = appender.list.stream()
                    .anyMatch(event -> event.getLevel() == Level.ERROR
                            && event.getFormattedMessage().contains(partitionKey));
            assertThat(errorLogged)
                    .as("an ERROR log line naming the dead-lettered partition must exist")
                    .isTrue();
        } finally {
            instance.close();
        }
    }

    private String findPartitionKeyNotOwnedByInstance(int instanceIndex) {
        ConsistentHashPartitionShardResolver resolver =
                new ConsistentHashPartitionShardResolver(instanceIndex, TOTAL_INSTANCES);
        for (int i = 0; i < 1000; i++) {
            String candidate = "dead-letter-test-partition-" + i;
            if (!resolver.ownsPartition(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("could not find a partition key not owned by instance " + instanceIndex);
    }

    private AuditEventEnvelope buildEnvelope(String partitionKey) {
        return new AuditEventEnvelope(
                AuditEventEnvelope.CURRENT_SCHEMA_VERSION,
                partitionKey,
                "DEAD_LETTER_TEST_EVENT",
                "SERVICE",
                "actor-1",
                "DATA_MUTATION",
                Instant.parse("2024-06-01T12:00:00Z"),
                Map.of(),
                null,
                null,
                null,
                Map.of());
    }

    private ConfigurableApplicationContext buildInstance(int instanceIndex) {
        return new SpringApplicationBuilder(TestApplication.class, QueueConfig.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword(),
                        "spring.mongodb.uri=" + MONGO.getReplicaSetUrl(),
                        "spring.rabbitmq.host=" + RABBIT.getHost(),
                        "spring.rabbitmq.port=" + RABBIT.getAmqpPort(),
                        "spring.rabbitmq.username=" + RABBIT.getAdminUsername(),
                        "spring.rabbitmq.password=" + RABBIT.getAdminPassword(),
                        "audit.rabbit.queue=" + QUEUE_NAME,
                        "audit.rabbit.shard.instance-index=" + instanceIndex,
                        "audit.rabbit.shard.total-instances=" + TOTAL_INSTANCES)
                .run();
    }

    @Configuration
    static class QueueConfig {

        @Bean
        Queue deadLetterTestQueue() {
            return new Queue(QUEUE_NAME, true);
        }
    }
}
