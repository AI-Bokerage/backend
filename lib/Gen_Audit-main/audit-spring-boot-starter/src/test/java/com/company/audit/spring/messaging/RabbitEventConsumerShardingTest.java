package com.company.audit.spring.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.company.audit.spring.port.EventStore;
import com.company.audit.spring.sharding.ConsistentHashPartitionShardResolver;
import com.company.audit.spring.support.AbstractFullStackIntegrationTest;
import com.company.audit.spring.support.TestApplication;
import com.company.audit.spring.wire.AuditEventEnvelope;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Two fully independent {@code ConfigurableApplicationContext}s — same pattern as Phase 8's
 * {@code CrossInstanceOrderingTest}, no shared Java object beyond this test method — both
 * configured with {@code audit.rabbit.shard.total-instances=2} and a distinct
 * {@code instance-index} each, sharing one real Testcontainers RabbitMQ queue. Publishes messages
 * across many distinct partition keys and confirms every one is recorded exactly once, by its
 * owning instance — proving correct ownership-based processing, not merely that the code runs.
 */
class RabbitEventConsumerShardingTest extends AbstractFullStackIntegrationTest {

    private static final int TOTAL_INSTANCES = 2;
    private static final int PARTITION_COUNT = 12;
    private static final String QUEUE_NAME = "sharding-test-queue-" + UUID.randomUUID();

    @Test
    void everyPartitionIsRecordedExactlyOnceByItsOwningInstance() throws Exception {
        ConfigurableApplicationContext instance0 = buildInstance(0);
        ConfigurableApplicationContext instance1 = buildInstance(1);
        try {
            RabbitTemplate rabbitTemplate = instance0.getBean(RabbitTemplate.class);
            EventStore eventStore = instance0.getBean(EventStore.class);

            List<String> partitionKeys = java.util.stream.IntStream.range(0, PARTITION_COUNT)
                    .mapToObj(i -> "sharding-test-partition-" + i + "-" + UUID.randomUUID())
                    .toList();

            for (String partitionKey : partitionKeys) {
                rabbitTemplate.convertAndSend(QUEUE_NAME, buildEnvelope(partitionKey));
            }

            for (String partitionKey : partitionKeys) {
                await().atMost(Duration.ofSeconds(20))
                        .untilAsserted(
                                () -> assertThat(eventStore.findByPartitionKey(partitionKey)).hasSize(1));
            }

            // Recorded exactly once each — not zero (lost), not more than one (double-processed
            // by both instances).
            for (String partitionKey : partitionKeys) {
                assertThat(eventStore.findByPartitionKey(partitionKey)).hasSize(1);
            }

            ConsistentHashPartitionShardResolver resolver0 = new ConsistentHashPartitionShardResolver(0, TOTAL_INSTANCES);
            long expectedOwnedByInstance0 =
                    partitionKeys.stream().filter(resolver0::ownsPartition).count();
            long expectedOwnedByInstance1 = partitionKeys.size() - expectedOwnedByInstance0;

            MeterRegistry meterRegistry0 = instance0.getBean(MeterRegistry.class);
            MeterRegistry meterRegistry1 = instance1.getBean(MeterRegistry.class);

            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertThat(acceptedCount(meterRegistry0))
                            .isEqualTo(expectedOwnedByInstance0));
            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertThat(acceptedCount(meterRegistry1))
                            .isEqualTo(expectedOwnedByInstance1));

            // Not an exact count: RabbitMQ round-robins a shared queue's deliveries across both
            // competing consumers, so a message can land directly on its owning instance on the
            // very first delivery, or on the other one first (triggering exactly the
            // republish-and-retry this phase's mechanism exists for) — either is a correct
            // outcome, so only presence of rejection activity is asserted, not an exact tally.
            // The accepted-count assertions above are the ones that prove exact correctness.
            assertThat(mismatchCount(meterRegistry0) + mismatchCount(meterRegistry1))
                    .as("at least one message must have been misdelivered at least once, for the "
                            + "shard-mismatch path to have actually been exercised by this test")
                    .isGreaterThan(0);
        } finally {
            instance0.close();
            instance1.close();
        }
    }

    private double acceptedCount(MeterRegistry meterRegistry) {
        var counter = meterRegistry.find("audit.rabbit.shard.ownership").tag("outcome", "accepted").counter();
        return counter == null ? 0.0 : counter.count();
    }

    private double mismatchCount(MeterRegistry meterRegistry) {
        var counter = meterRegistry.find("audit.rabbit.shard.ownership").tag("outcome", "mismatch").counter();
        return counter == null ? 0.0 : counter.count();
    }

    private AuditEventEnvelope buildEnvelope(String partitionKey) {
        return new AuditEventEnvelope(
                AuditEventEnvelope.CURRENT_SCHEMA_VERSION,
                partitionKey,
                "SHARDING_TEST_EVENT",
                "SERVICE",
                "actor-1",
                "DATA_MUTATION",
                Instant.parse("2024-06-01T12:00:00Z"),
                Map.of("partitionKey", partitionKey),
                null,
                null,
                null,
                Map.of());
    }

    private ConfigurableApplicationContext buildInstance(int instanceIndex) {
        return new SpringApplicationBuilder(TestApplication.class, QueueConfig.class, MeterRegistryConfig.class)
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
        Queue shardingTestQueue() {
            return new Queue(QUEUE_NAME, true);
        }
    }

    @Configuration
    static class MeterRegistryConfig {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
