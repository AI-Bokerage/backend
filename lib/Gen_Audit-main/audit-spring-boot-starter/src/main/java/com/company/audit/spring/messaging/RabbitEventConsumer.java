package com.company.audit.spring.messaging;

import com.company.audit.spring.ingestion.AuditRecorder;
import com.company.audit.spring.metrics.AuditMetricsRecorder;
import com.company.audit.spring.sharding.PartitionShardResolver;
import com.company.audit.spring.wire.AuditEventEnvelope;
import com.company.audit.spring.wire.AuditEventEnvelopeMapper;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SmartMessageConverter;
import org.springframework.core.ParameterizedTypeReference;

/**
 * Consumes {@link AuditEventEnvelope} messages from Rabbit and records them via
 * {@link AuditRecorder} — but only for partitions this instance actually owns, per
 * {@link PartitionShardResolver}.
 *
 * <p>Acknowledgment for an <em>owned</em> partition is entirely Spring AMQP's default behavior:
 * the listener container acknowledges only if this method returns normally, and
 * nacks-and-requeues if it throws. No manual ack/nack logic lives on that path — a poison message
 * looping visibly is an acceptable failure mode for this phase; a silently dropped audit event is
 * not.
 *
 * <p><b>A partition this instance doesn't own is never processed at all</b> — never mapped, never
 * handed to {@link AuditRecorder}. It is republished to the same queue with an incremented retry
 * counter (a custom header, {@value #SHARD_RETRY_COUNT_HEADER}), and this delivery is acknowledged
 * normally so the broker doesn't <em>also</em> requeue the original — avoiding a duplicate copy.
 * Once that counter crosses {@value #MAX_SHARD_MISMATCH_REDELIVERIES}, the message is redirected
 * to the dead-letter queue instead of being republished again, logged at {@code ERROR}.
 *
 * <p><b>Why a custom header instead of RabbitMQ's native {@code x-death}/redelivery tracking:</b>
 * {@code x-death} only populates once a message has actually passed through a broker-configured
 * dead-letter exchange — and this starter deliberately does not declare the main queue at all
 * (queue provisioning has been an application-level concern since Phase 4), so it has no queue
 * whose arguments it could set a self-referencing DLX on. A self-managed header on a message this
 * class itself republishes needs no queue-argument cooperation from whichever application
 * declared the main queue, at the cost of only tracking rejections this class caused (not, for
 * example, genuine processing-exception redeliveries, which is exactly the scope this counter
 * needs to cover).
 *
 * <p><b>This proves ownership filtering works — it is not the final production distribution
 * strategy.</b> Plain requeue-with-a-counter on a shared queue with multiple competing consumers
 * still means a rejected message sits in the same queue every other consumer also competes for;
 * real deployments typically solve steady-state distribution differently (a queue-per-shard
 * topology, or RabbitMQ's own consistent-hash-exchange plugin) — broker/deployment configuration,
 * correctly out of scope for this library.
 *
 * <p>Depends only on the plain {@link AuditMetricsRecorder} interface, never on Micrometer or any
 * specific implementation — see that interface's Javadoc for the class-loading constraint that
 * makes this non-negotiable. {@link Message}, {@link MessageConverter}, and {@link RabbitTemplate}
 * are safe to name directly here despite that same constraint: they belong to the very dependency
 * (Spring AMQP) this class's entire auto-configuration is already gated on
 * ({@code @ConditionalOnClass(RabbitListener.class)}), so if that dependency were absent this
 * class would never be loaded at all — unlike Micrometer, which can be absent even when Spring
 * AMQP is present.
 */
public class RabbitEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RabbitEventConsumer.class);

    /** The custom header this class uses to track shard-mismatch redeliveries it caused. */
    public static final String SHARD_RETRY_COUNT_HEADER = "x-audit-shard-retry-count";

    /** How many shard-mismatch redeliveries a message may accumulate before being dead-lettered. */
    public static final int MAX_SHARD_MISMATCH_REDELIVERIES = 5;

    private final AuditRecorder auditRecorder;
    private final AuditEventEnvelopeMapper mapper;
    private final AuditMetricsRecorder metricsRecorder;
    private final PartitionShardResolver shardResolver;
    private final MessageConverter messageConverter;
    private final RabbitTemplate rabbitTemplate;
    private final String queueName;
    private final String deadLetterQueueName;
    private final int instanceIndex;
    private final int totalInstances;

    /**
     * Creates a new consumer.
     *
     * @param auditRecorder records mapped events into the ledger and rich event store
     * @param mapper maps the wire envelope into {@code audit-core}'s domain event
     * @param metricsRecorder records consumption outcomes and timing
     * @param shardResolver decides whether this instance owns an inbound message's partition
     * @param messageConverter converts the raw inbound message into an {@link AuditEventEnvelope}
     * @param rabbitTemplate used to republish a shard-mismatched message, or dead-letter it
     * @param queueName the queue this consumer listens on, and republishes shard mismatches to
     * @param deadLetterQueueName the queue a message is redirected to once it exceeds
     *     {@value #MAX_SHARD_MISMATCH_REDELIVERIES} shard-mismatch redeliveries
     * @param instanceIndex this instance's own configured shard index, for diagnostic logging only
     * @param totalInstances the configured total instance count, for diagnostic logging only
     */
    public RabbitEventConsumer(
            AuditRecorder auditRecorder,
            AuditEventEnvelopeMapper mapper,
            AuditMetricsRecorder metricsRecorder,
            PartitionShardResolver shardResolver,
            MessageConverter messageConverter,
            RabbitTemplate rabbitTemplate,
            String queueName,
            String deadLetterQueueName,
            int instanceIndex,
            int totalInstances) {
        this.auditRecorder = auditRecorder;
        this.mapper = mapper;
        this.metricsRecorder = metricsRecorder;
        this.shardResolver = shardResolver;
        this.messageConverter = messageConverter;
        this.rabbitTemplate = rabbitTemplate;
        this.queueName = queueName;
        this.deadLetterQueueName = deadLetterQueueName;
        this.instanceIndex = instanceIndex;
        this.totalInstances = totalInstances;
    }

    /**
     * Handles one inbound message.
     *
     * @param rawMessage the inbound message, converted manually (rather than via this method's
     *     own parameter type) so this class can inspect the partition key and decide ownership
     *     before ever handing the event to {@link AuditRecorder}
     */
    @RabbitListener(queues = "${audit.rabbit.queue:audit.events}")
    public void onMessage(Message rawMessage) {
        AuditEventEnvelope envelope = convert(rawMessage);
        String partitionKey = envelope.partitionKey();

        if (!shardResolver.ownsPartition(partitionKey)) {
            handleShardMismatch(rawMessage, partitionKey);
            return;
        }
        metricsRecorder.recordShardOwnershipAccepted(partitionKey);

        Instant startedAt = Instant.now();
        try {
            auditRecorder.record(mapper.toDomain(envelope));
            metricsRecorder.recordMessageConsumed(true, Duration.between(startedAt, Instant.now()));
        } catch (RuntimeException e) {
            metricsRecorder.recordMessageConsumed(false, Duration.between(startedAt, Instant.now()));
            throw e;
        }
    }

    private void handleShardMismatch(Message rawMessage, String partitionKey) {
        metricsRecorder.recordShardMismatch(partitionKey);
        long priorRetries = readShardRetryCount(rawMessage);
        int ownerIndex = Math.floorMod(partitionKey.hashCode(), totalInstances);
        log.warn(
                "shard mismatch partitionKey={} thisInstanceIndex={} computedOwnerIndex={} priorRetries={}",
                partitionKey,
                instanceIndex,
                ownerIndex,
                priorRetries);

        if (priorRetries + 1 > MAX_SHARD_MISMATCH_REDELIVERIES) {
            log.error(
                    "dead-lettering after {} shard-mismatch redeliveries partitionKey={}", priorRetries, partitionKey);
            rabbitTemplate.send(deadLetterQueueName, rawMessage);
            return;
        }

        Message republished = withIncrementedRetryHeader(rawMessage, priorRetries + 1);
        rabbitTemplate.send(queueName, republished);
    }

    /**
     * Converts the raw message, passing {@link AuditEventEnvelope}{@code .class} as an explicit
     * conversion hint whenever the configured converter supports one.
     *
     * <p>The previous {@code @RabbitListener(AuditEventEnvelope envelope)} signature let Spring
     * infer the target type from the method parameter itself, which works even when a message
     * has no {@code __TypeId__} header at all — that header is only ever set automatically by
     * Spring's own {@code convertAndSend}, never by an external, non-Spring producer. Calling the
     * single-arg {@link MessageConverter#fromMessage(Message)} here instead would silently
     * regress to requiring that header, breaking exactly the producers most likely to lack it —
     * found by actually publishing a raw envelope with no {@code __TypeId__} header via
     * {@code audit-demo}, not assumed. {@link SmartMessageConverter#fromMessage(Message, Object)}
     * (which {@link com.company.audit.spring.autoconfigure.AuditRabbitAutoConfiguration}'s own
     * {@code Jackson2JsonMessageConverter} implements) restores that same type-inference
     * capability; a plain {@link MessageConverter} supplied by a consuming application falls back
     * to requiring the header, exactly matching that converter's own actual capability.
     *
     * <p>The hint must be a {@link ParameterizedTypeReference}, not a plain {@code Class} — a
     * plain {@code Class} passed as {@code conversionHint} is silently ignored by
     * {@code AbstractJackson2MessageConverter}, which only special-cases
     * {@code ParameterizedTypeReference} before falling back to the {@code __TypeId__} header —
     * confirmed by reading that converter's actual source after a raw {@code Class} hint alone
     * still failed with the same {@code ClassCastException}, not assumed from the method
     * signature accepting {@code Object} alone.
     *
     * @param rawMessage the inbound message to convert
     * @return the converted envelope
     */
    private AuditEventEnvelope convert(Message rawMessage) {
        Object converted = messageConverter instanceof SmartMessageConverter smartConverter
                ? smartConverter.fromMessage(rawMessage, ParameterizedTypeReference.forType(AuditEventEnvelope.class))
                : messageConverter.fromMessage(rawMessage);
        return (AuditEventEnvelope) converted;
    }

    private long readShardRetryCount(Message message) {
        Object value = message.getMessageProperties().getHeaders().get(SHARD_RETRY_COUNT_HEADER);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private Message withIncrementedRetryHeader(Message original, long newCount) {
        var properties = MessagePropertiesBuilder.fromClonedProperties(original.getMessageProperties())
                .setHeader(SHARD_RETRY_COUNT_HEADER, newCount)
                .build();
        return new Message(original.getBody(), properties);
    }
}
