package com.company.audit.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.audit.core.api.AuditEvent;
import com.company.audit.core.api.ChainedRecord;
import com.company.audit.core.api.PartitionContext;
import com.company.audit.spring.ingestion.AuditRecorder;
import com.company.audit.spring.messaging.RabbitEventConsumer;
import com.company.audit.spring.port.EventStore;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Proves the {@code @ConditionalOnBean(AuditRecorder.class)} gate on
 * {@link AuditRabbitAutoConfiguration} — the consumer must appear only when there is something
 * to hand events to, not merely when Spring AMQP is on the classpath.
 */
class AuditRabbitAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(AuditMetricsAutoConfiguration.class, AuditRabbitAutoConfiguration.class));

    @Test
    void withNoAuditRecorderBeanNoRabbitEventConsumerIsCreated() {
        runner.run(context -> assertThat(context).doesNotHaveBean(RabbitEventConsumer.class));
    }

    @Test
    void withAnAuditRecorderBeanRabbitEventConsumerIsCreated() {
        runner.withUserConfiguration(FakeAuditRecorderConfig.class)
                .run(context -> assertThat(context).hasSingleBean(RabbitEventConsumer.class));
    }

    @Configuration
    static class FakeAuditRecorderConfig {

        @Bean
        AuditRecorder auditRecorder() {
            return new AuditRecorder(
                    partitionKey -> new PartitionContext(partitionKey, Instant.EPOCH),
                    (event, partitionContext) -> {
                        throw new UnsupportedOperationException("not exercised by this test");
                    },
                    new EventStore() {
                        @Override
                        public void save(AuditEvent event, ChainedRecord record) {
                            // not exercised by this test
                        }

                        @Override
                        public List<AuditEvent> findByPartitionKey(String partitionKey) {
                            return List.of();
                        }
                    });
        }

        // A RabbitTemplate is normally provided by Spring Boot's own RabbitAutoConfiguration,
        // not included in this test's narrow AutoConfigurations.of(...) list. Constructing one
        // against a CachingConnectionFactory never eagerly connects, matching this project's
        // established pattern for other lazy-connecting test fakes (MongoTemplate, etc.).
        @Bean
        RabbitTemplate rabbitTemplate() {
            return new RabbitTemplate(new CachingConnectionFactory("localhost"));
        }
    }
}
