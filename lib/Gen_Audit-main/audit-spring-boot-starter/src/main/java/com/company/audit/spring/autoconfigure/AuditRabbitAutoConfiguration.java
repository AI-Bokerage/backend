package com.company.audit.spring.autoconfigure;

import com.company.audit.spring.config.AuditProperties;
import com.company.audit.spring.ingestion.AuditRecorder;
import com.company.audit.spring.messaging.RabbitEventConsumer;
import com.company.audit.spring.metrics.AuditMetricsRecorder;
import com.company.audit.spring.sharding.ConsistentHashPartitionShardResolver;
import com.company.audit.spring.sharding.PartitionShardResolver;
import com.company.audit.spring.wire.AuditEventEnvelopeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the Rabbit consumer that feeds {@link AuditRecorder}.
 *
 * <p>Meaningless without something to hand events to: gated on an {@link AuditRecorder} bean
 * being present, not merely on Spring AMQP being on the classpath.
 *
 * <p>Registers a {@link Jackson2JsonMessageConverter}, not the newer Jackson-3-based
 * {@code JacksonJsonMessageConverter} also present in this version of {@code spring-amqp}: this
 * project's resolved dependency tree pulls Jackson 2 (confirmed by inspecting
 * {@code compileClasspath}, not assumed), and the Jackson-3 converter would fail with a
 * {@code NoClassDefFoundError} against that classpath.
 *
 * <p>{@link RabbitEventConsumer} now depends only on the plain {@link AuditMetricsRecorder}
 * interface, always available via {@code AuditMetricsAutoConfiguration} (see that class and
 * {@link AuditMetricsRecorder}'s Javadoc for the class-loading constraint that used to require a
 * nested {@code @ConditionalOnClass(MeterRegistry.class)} configuration here — no longer needed
 * now that Micrometer-awareness lives entirely behind that interface).
 *
 * <p>Also provides the default {@link PartitionShardResolver} and the dead-letter {@link Queue}
 * Phase 9's sharding mechanism needs. Unlike the main queue (still not declared here — queue
 * provisioning has been an application-level concern since Phase 4), the dead-letter queue *is*
 * declared by this auto-configuration: it is new infrastructure this capability itself
 * introduces, not an assumption about topology a consuming application already owns.
 */
@AutoConfiguration(after = AuditIngestionAutoConfiguration.class)
@ConditionalOnClass(RabbitListener.class)
@ConditionalOnBean(AuditRecorder.class)
@EnableConfigurationProperties(AuditProperties.class)
public class AuditRabbitAutoConfiguration {

    /**
     * Creates a new auto-configuration instance.
     */
    public AuditRabbitAutoConfiguration() {
    }

    /**
     * Provides the mapper bean between the wire envelope and {@code audit-core}'s domain event.
     *
     * @return a new mapper
     */
    @Bean
    public AuditEventEnvelopeMapper auditEventEnvelopeMapper() {
        return new AuditEventEnvelopeMapper();
    }

    /**
     * Provides the JSON message converter {@code @RabbitListener} uses to bind inbound messages
     * to {@code AuditEventEnvelope}, unless a consuming application has already supplied its own
     * {@link MessageConverter} bean.
     *
     * <p>{@code spring-amqp} 4.x marks {@link Jackson2JsonMessageConverter} deprecated for
     * removal in favor of a Jackson-3-based {@code JacksonJsonMessageConverter}, but this
     * project's resolved dependency tree pulls Jackson 2, not Jackson 3 (confirmed against
     * {@code compileClasspath}) — the Jackson-3 converter would fail with a
     * {@code NoClassDefFoundError} at runtime against this classpath. Revisit once/if this
     * starter moves to Jackson 3.
     *
     * <p>Builds its own Jackson-2 {@link ObjectMapper} rather than injecting a Boot-managed one:
     * Boot 4's own Jackson auto-configuration ({@code spring-boot-jackson}) produces a
     * Jackson-<em>3</em> {@code tools.jackson.databind.ObjectMapper} — an entirely different
     * class, not assignable to this one — so there is no compatible bean to inject, confirmed by
     * an actual {@code NoSuchBeanDefinitionException} naming the Jackson-2 type. The
     * {@link JavaTimeModule} registration is required for {@code java.time.Instant} fields on
     * {@code AuditEventEnvelope}; without it, conversion fails with
     * {@code InvalidDefinitionException} — also confirmed by an actual failure, not assumed.
     *
     * @return a new Jackson-2-based converter
     */
    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    @SuppressWarnings("removal")
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * Provides the default {@link PartitionShardResolver}, unless a consuming application has
     * already supplied its own. Defaults ({@code instanceIndex=0}, {@code totalInstances=1}) make
     * this a no-op for every single-instance deployment — see {@code AuditProperties.Rabbit.Shard}.
     *
     * @param properties the starter's configuration properties
     * @return a new resolver
     */
    @Bean
    @ConditionalOnMissingBean(PartitionShardResolver.class)
    public ConsistentHashPartitionShardResolver partitionShardResolver(AuditProperties properties) {
        AuditProperties.Rabbit.Shard shard = properties.getRabbit().getShard();
        return new ConsistentHashPartitionShardResolver(shard.getInstanceIndex(), shard.getTotalInstances());
    }

    /**
     * Provides the dead-letter queue a message is redirected to once it exceeds
     * {@value RabbitEventConsumer#MAX_SHARD_MISMATCH_REDELIVERIES} shard-mismatch redeliveries,
     * unless a consuming application has already supplied its own queue bean of that name.
     * Named {@code <queue>.dead-letter} by convention, not independently configurable — this is
     * new infrastructure Phase 9 introduces, kept to one fixed convention rather than a third
     * property this early.
     *
     * @param properties the starter's configuration properties
     * @return a new durable queue
     */
    @Bean
    @ConditionalOnMissingBean(name = "auditDeadLetterQueue")
    public Queue auditDeadLetterQueue(AuditProperties properties) {
        return new Queue(deadLetterQueueName(properties.getRabbit().getQueue()), true);
    }

    /**
     * Provides the {@link RabbitEventConsumer} bean, unless a consuming application has already
     * supplied its own.
     *
     * @param auditRecorder records mapped events into the ledger and rich event store
     * @param mapper maps the wire envelope into {@code audit-core}'s domain event
     * @param metricsRecorder records consumption outcomes and timing
     * @param shardResolver decides whether this instance owns an inbound message's partition
     * @param messageConverter converts the raw inbound message into an {@code AuditEventEnvelope}
     * @param rabbitTemplate used to republish a shard-mismatched message, or dead-letter it
     * @param properties the starter's configuration properties
     * @return a new consumer
     */
    @Bean
    @ConditionalOnMissingBean(RabbitEventConsumer.class)
    public RabbitEventConsumer rabbitEventConsumer(
            AuditRecorder auditRecorder,
            AuditEventEnvelopeMapper mapper,
            AuditMetricsRecorder metricsRecorder,
            PartitionShardResolver shardResolver,
            MessageConverter messageConverter,
            RabbitTemplate rabbitTemplate,
            AuditProperties properties) {
        AuditProperties.Rabbit rabbit = properties.getRabbit();
        return new RabbitEventConsumer(
                auditRecorder,
                mapper,
                metricsRecorder,
                shardResolver,
                messageConverter,
                rabbitTemplate,
                rabbit.getQueue(),
                deadLetterQueueName(rabbit.getQueue()),
                rabbit.getShard().getInstanceIndex(),
                rabbit.getShard().getTotalInstances());
    }

    private static String deadLetterQueueName(String queueName) {
        return queueName + ".dead-letter";
    }
}
