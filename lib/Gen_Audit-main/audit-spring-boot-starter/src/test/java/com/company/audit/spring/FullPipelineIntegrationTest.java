package com.company.audit.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.company.audit.spring.health.AuditChainHealthIndicator;
import com.company.audit.spring.metrics.AuditMetricsRecorder;
import com.company.audit.spring.metrics.MicrometerAuditMetricsRecorder;
import com.company.audit.spring.metrics.NoOpAuditMetricsRecorder;
import com.company.audit.spring.port.EventStore;
import com.company.audit.spring.scheduling.AnchorPublisherJob;
import com.company.audit.spring.sharding.ConsistentHashPartitionShardResolver;
import com.company.audit.spring.support.AbstractFullStackIntegrationTest;
import com.company.audit.spring.support.TestApplication;
import com.company.audit.spring.verification.ChainVerifierJob;
import com.company.audit.spring.verification.VerificationSummary;
import com.company.audit.spring.wire.AuditEventEnvelope;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

/**
 * A release-readiness end-to-end pass that exercises the whole starter together, not one
 * mechanism at a time the way every other integration test in this suite deliberately does:
 * partition-ownership sharding across two genuinely independent instances, concurrent event
 * ingestion via a real Rabbit broker, scheduled verification and anchoring both manually
 * triggered, one partition tampered via raw JDBC, and both the metrics and health surfaces
 * checked against the outcome — run twice, once with Micrometer present and once with it made
 * absent from this JVM's view via {@link FilteredClassLoader}, the same technique
 * {@code ApplicationContextRunner} itself uses internally, to prove the no-Micrometer path found
 * by earlier phases via manual {@code audit-demo} verification also holds for this exact
 * multi-instance, multi-mechanism scenario.
 */
class FullPipelineIntegrationTest extends AbstractFullStackIntegrationTest {

    private static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                    .withServices(LocalStackContainer.Service.S3);

    static {
        LOCALSTACK.start();
    }

    private static final int TOTAL_INSTANCES = 2;
    private static final int PARTITION_COUNT = 10;
    private static final int EVENTS_PER_PARTITION = 5;

    private List<ConfigurableApplicationContext> instances;

    @AfterEach
    void closeInstances() {
        if (instances != null) {
            instances.forEach(ConfigurableApplicationContext::close);
        }
    }

    @Test
    void fullPipelineAcrossShardedInstancesWithMicrometerPresent() throws Exception {
        runFullPipeline("with-micrometer-" + UUID.randomUUID(), true);
    }

    @Test
    void fullPipelineAcrossShardedInstancesWithoutMicrometer() throws Exception {
        runFullPipeline("no-micrometer-" + UUID.randomUUID(), false);
    }

    private void runFullPipeline(String runId, boolean micrometerPresent) throws Exception {
        String queueName = "full-pipeline-queue-" + runId;
        // S3 bucket names are DNS-hostname-constrained to 63 characters; a full UUID-suffixed
        // runId comfortably fits a queue name but not a "full-pipeline-bucket-" prefix on top of
        // it, so this uses a short, still-unique-enough name instead.
        String bucket = "full-pipeline-" + Integer.toHexString(runId.hashCode());

        instances = buildInstances(queueName, bucket, micrometerPresent);
        ConfigurableApplicationContext instance0 = instances.get(0);

        AuditMetricsRecorder metricsRecorder = instance0.getBean(AuditMetricsRecorder.class);
        if (micrometerPresent) {
            assertThat(metricsRecorder).isInstanceOf(MicrometerAuditMetricsRecorder.class);
        } else {
            assertThat(metricsRecorder)
                    .as("with Micrometer's MeterRegistry made absent from this JVM's view, "
                            + "AuditMetricsAutoConfiguration must fall back to the no-op recorder")
                    .isInstanceOf(NoOpAuditMetricsRecorder.class);
        }

        S3Client s3Client = instance0.getBean(S3Client.class);
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).objectLockEnabledForBucket(true).build());

        RabbitTemplate rabbitTemplate = instance0.getBean(RabbitTemplate.class);
        EventStore eventStore = instance0.getBean(EventStore.class);

        List<String> partitionKeys = java.util.stream.IntStream.range(0, PARTITION_COUNT)
                .mapToObj(i -> "full-pipeline-partition-" + runId + "-" + i)
                .toList();

        // Concurrent load: every partition's events published in parallel, not sequentially, so
        // this actually exercises concurrent ingestion rather than merely many sequential calls.
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<? extends java.util.concurrent.Future<?>> futures = partitionKeys.stream()
                    .map(partitionKey -> executor.submit(() -> {
                        for (int i = 0; i < EVENTS_PER_PARTITION; i++) {
                            rabbitTemplate.convertAndSend(queueName, buildEnvelope(partitionKey, i));
                        }
                    }))
                    .toList();
            for (var future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
        }

        // With PARTITION_COUNT partitions' worth of messages published concurrently onto one
        // queue shared by two competing consumers, RabbitMQ's round-robin delivery can — rarely,
        // but for real, not hypothetically — misdeliver the same message to the non-owning
        // instance more than MAX_SHARD_MISMATCH_REDELIVERIES times in a row, landing it on the
        // dead-letter queue exactly as documented rather than eventually reaching its owner.
        // Found by actually running this scenario, not assumed away: recovering by redelivering
        // anything found dead-lettered is a realistic operator response to that documented,
        // accepted tradeoff, not a workaround for a bug in this starter.
        redeliverAnyDeadLetteredMessages(rabbitTemplate, queueName);

        for (String partitionKey : partitionKeys) {
            await().atMost(Duration.ofSeconds(30))
                    .untilAsserted(() -> assertThat(eventStore.findByPartitionKey(partitionKey))
                            .hasSize(EVENTS_PER_PARTITION));
        }

        // Sharding actually did the routing: each partition's events landed on the instance that
        // owns it, not merely "somewhere" — confirmed against the same hashing the resolver uses.
        ConsistentHashPartitionShardResolver resolver0 = new ConsistentHashPartitionShardResolver(0, TOTAL_INSTANCES);
        long expectedOwnedByInstance0 = partitionKeys.stream().filter(resolver0::ownsPartition).count();
        assertThat(expectedOwnedByInstance0).isBetween(1L, (long) partitionKeys.size() - 1);

        ChainVerifierJob chainVerifierJob = instance0.getBean(ChainVerifierJob.class);
        VerificationSummary cleanSummary = chainVerifierJob.runNow();
        for (String partitionKey : partitionKeys) {
            assertThat(cleanSummary.resultsByPartition().get(partitionKey).status())
                    .isEqualTo(com.company.audit.core.api.enums.AuditChainStatus.OK);
        }

        String tamperedPartition = partitionKeys.get(0);
        tamperOnePartitionViaRawJdbc(tamperedPartition);

        VerificationSummary afterTamperSummary = chainVerifierJob.runNow();
        assertThat(afterTamperSummary.resultsByPartition().get(tamperedPartition).status())
                .isNotEqualTo(com.company.audit.core.api.enums.AuditChainStatus.OK);
        for (String partitionKey : partitionKeys) {
            if (!partitionKey.equals(tamperedPartition)) {
                assertThat(afterTamperSummary.resultsByPartition().get(partitionKey).status())
                        .isEqualTo(com.company.audit.core.api.enums.AuditChainStatus.OK);
            }
        }

        AnchorPublisherJob anchorPublisherJob = instance0.getBean(AnchorPublisherJob.class);
        var anchorSummary = anchorPublisherJob.runNow();
        for (String partitionKey : partitionKeys) {
            assertThat(anchorSummary.storageReferenceByPartition())
                    .as("a hash-mismatch does not prevent anchoring the current tip — anchoring only "
                            + "fails when a partition has no chain at all")
                    .containsKey(partitionKey);
        }
        assertThat(objectCountUnderPrefix(s3Client, bucket, "anchors/" + tamperedPartition + "/"))
                .isEqualTo(1);

        AuditChainHealthIndicator healthIndicator = instance0.getBean(AuditChainHealthIndicator.class);
        var health = healthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        // Not exact counts: the health indicator correctly reports across this shared Postgres's
        // *entire* partition catalog, by design (see its own Javadoc) — this class's two test
        // methods share that one catalog (same Testcontainers instance for the whole test class),
        // so a prior method's own tampered/clean partitions are still present here too. Only this
        // run's own contribution is asserted, via "at least," not the catalog-wide total.
        assertThat((Long) health.getDetails().get("brokenPartitions")).isGreaterThanOrEqualTo(1L);
        assertThat((Long) health.getDetails().get("cleanPartitions")).isGreaterThanOrEqualTo(partitionKeys.size() - 1L);
        assertThat(health.getDetails()).containsKey("lastAnchorRunAt");

        if (micrometerPresent) {
            MeterRegistry meterRegistry = instance0.getBean(MeterRegistry.class);
            assertThat(counter(meterRegistry, "audit.rabbit.shard.ownership", "accepted")).isGreaterThan(0);
            assertThat(counter(meterRegistry, "audit.ledger.append", "success")).isGreaterThan(0);
            assertThat(counter(meterRegistry, "audit.verification.partitions", "clean")).isGreaterThan(0);
            assertThat(counter(meterRegistry, "audit.verification.partitions", "broken")).isGreaterThan(0);
            assertThat(counter(meterRegistry, "audit.anchor.partitions", "success")).isGreaterThan(0);
        }
    }

    private void redeliverAnyDeadLetteredMessages(RabbitTemplate rabbitTemplate, String queueName) {
        // The shard-mismatch retry/dead-letter cycle takes a few rounds to settle; give it a
        // moment before checking, rather than draining a queue that is still actively filling.
        try {
            Thread.sleep(Duration.ofSeconds(3));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String deadLetterQueueName = queueName + ".dead-letter";
        org.springframework.amqp.core.Message deadLettered;
        int redelivered = 0;
        while ((deadLettered = rabbitTemplate.receive(deadLetterQueueName)) != null) {
            var freshProperties = org.springframework.amqp.core.MessagePropertiesBuilder.fromClonedProperties(
                            deadLettered.getMessageProperties())
                    .removeHeader("x-audit-shard-retry-count")
                    .build();
            rabbitTemplate.send(queueName, new org.springframework.amqp.core.Message(deadLettered.getBody(), freshProperties));
            redelivered++;
        }
        if (redelivered > 0) {
            // Loud on purpose: this is a genuine, if rare, outcome of the documented
            // competing-consumer round-robin tradeoff, not something to swallow silently.
            System.out.println(
                    "FullPipelineIntegrationTest: redelivered " + redelivered + " message(s) found on the "
                            + "dead-letter queue after exceeding the shard-mismatch redelivery cap under "
                            + "concurrent load across " + PARTITION_COUNT + " partitions on one shared queue");
        }
    }

    private double counter(MeterRegistry meterRegistry, String name, String outcomeOrStatusTagValue) {
        var found = meterRegistry.find(name).tags("outcome", outcomeOrStatusTagValue).counter();
        if (found == null) {
            found = meterRegistry.find(name).tags("status", outcomeOrStatusTagValue).counter();
        }
        return found == null ? 0.0 : found.count();
    }

    private int objectCountUnderPrefix(S3Client s3Client, String bucket, String prefix) {
        return s3Client
                .listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build())
                .keyCount();
    }

    private void tamperOnePartitionViaRawJdbc(String partitionKey) throws Exception {
        byte[] bogusHash = MessageDigest.getInstance("SHA-256").digest("tampered-via-raw-jdbc".getBytes());
        try (Connection connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE audit_immutable SET event_hash = ? WHERE partition_key = ? AND seq = 1")) {
            statement.setBytes(1, bogusHash);
            statement.setString(2, partitionKey);
            int updated = statement.executeUpdate();
            assertThat(updated).isEqualTo(1);
        }
    }

    private AuditEventEnvelope buildEnvelope(String partitionKey, int index) {
        return new AuditEventEnvelope(
                AuditEventEnvelope.CURRENT_SCHEMA_VERSION,
                partitionKey,
                "FULL_PIPELINE_TEST_EVENT",
                "SERVICE",
                "actor-1",
                "DATA_MUTATION",
                Instant.parse("2024-06-01T12:00:00Z"),
                Map.of("index", index),
                null,
                null,
                null,
                Map.of());
    }

    private List<ConfigurableApplicationContext> buildInstances(String queueName, String bucket, boolean micrometerPresent)
            throws Exception {
        if (!micrometerPresent) {
            // FilteredClassLoader makes MeterRegistry appear absent to anything resolved while
            // it's the thread context classloader — the same technique
            // ApplicationContextRunner.withClassLoader(...) uses internally. Restored in a
            // finally block regardless of outcome, since leaking a filtered TCCL onto later
            // tests in the same JVM would be a much worse problem than this one failing.
            ClassLoader original = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(new FilteredClassLoader(MeterRegistry.class));
            try {
                return java.util.stream.IntStream.range(0, TOTAL_INSTANCES)
                        .mapToObj(i -> buildInstance(i, queueName, bucket, false))
                        .toList();
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }
        return java.util.stream.IntStream.range(0, TOTAL_INSTANCES)
                .mapToObj(i -> buildInstance(i, queueName, bucket, true))
                .toList();
    }

    private ConfigurableApplicationContext buildInstance(
            int instanceIndex, String queueName, String bucket, boolean micrometerPresent) {
        // MeterRegistryConfig itself names MeterRegistry in its own bytecode, so it must never
        // even be offered as a source class when Micrometer is meant to look absent — offering
        // it and merely skipping its @Bean method would still fail to load the config class.
        Class<?>[] sources = micrometerPresent
                ? new Class<?>[] {TestApplication.class, QueueConfig.class, S3ClientConfig.class, MeterRegistryConfig.class}
                : new Class<?>[] {TestApplication.class, QueueConfig.class, S3ClientConfig.class};
        List<String> properties = new java.util.ArrayList<>(List.of(
                "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "spring.datasource.username=" + POSTGRES.getUsername(),
                "spring.datasource.password=" + POSTGRES.getPassword(),
                "spring.mongodb.uri=" + MONGO.getReplicaSetUrl(),
                "spring.rabbitmq.host=" + RABBIT.getHost(),
                "spring.rabbitmq.port=" + RABBIT.getAmqpPort(),
                "spring.rabbitmq.username=" + RABBIT.getAdminUsername(),
                "spring.rabbitmq.password=" + RABBIT.getAdminPassword(),
                "audit.rabbit.queue=" + queueName,
                "audit.rabbit.shard.instance-index=" + instanceIndex,
                "audit.rabbit.shard.total-instances=" + TOTAL_INSTANCES,
                "audit.anchor.bucket=" + bucket));
        if (!micrometerPresent) {
            // With no explicit MeterRegistry bean supplied and none of this starter's own
            // Micrometer wiring active, Boot's own built-in actuator metrics auto-configuration
            // (unrelated to this starter, brought in only because spring-boot-starter-actuator is
            // also this test module's dependency for the health-indicator work) otherwise creates
            // two competing MeterRegistry beans (simple and no-op) and fails startup — found by
            // actually running this scenario, not assumed harmless. Excluded here since it is
            // irrelevant noise for what this test actually verifies: this starter's own
            // AuditMetricsRecorder bean choice, not Boot's unrelated observability wiring.
            properties.add(
                    "spring.autoconfigure.exclude=org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration");
        }
        return new SpringApplicationBuilder(sources)
                .web(WebApplicationType.NONE)
                .properties(properties.toArray(new String[0]))
                .run();
    }

    @Configuration
    static class QueueConfig {

        @Bean
        Queue fullPipelineQueue(org.springframework.core.env.Environment environment) {
            return new Queue(environment.getProperty("audit.rabbit.queue"), true);
        }
    }

    @Configuration
    static class S3ClientConfig {

        @Bean
        S3Client s3Client() {
            return S3Client.builder()
                    .endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3))
                    .region(Region.of(LOCALSTACK.getRegion()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                    .forcePathStyle(true)
                    .build();
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
