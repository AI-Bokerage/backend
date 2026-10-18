package com.company.audit.spring.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.audit.core.api.AuditEvent;
import com.company.audit.core.api.enums.ActorType;
import com.company.audit.core.api.enums.AuditCategory;
import com.company.audit.spring.anchor.AnchorPublishFailedEvent;
import com.company.audit.spring.anchor.AnchorSummary;
import com.company.audit.spring.ingestion.AuditRecorder;
import com.company.audit.spring.partition.PartitionRegistry;
import com.company.audit.spring.support.AbstractPostgresAndMongoIntegrationTest;
import com.company.audit.spring.support.TestApplication;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

/**
 * Exercises {@link AnchorPublisherJob} against a real Testcontainers Postgres instance (for the
 * chain/partition tables) and a real Testcontainers LocalStack S3 endpoint (for anchor storage).
 *
 * <p>One of the three partitions is deliberately left with no chain at all (only registered via
 * {@link PartitionRegistry#resolve}, never appended to) so that {@code AnchorPublisher.publish}
 * throws for it — proving, with a real failing partition rather than an asserted comment, that
 * one partition's anchor-publish failure does not abort the job for the other two.
 */
@SpringBootTest(classes = TestApplication.class)
@Import(AnchorPublisherJobIntegrationTest.CapturingListenerConfig.class)
class AnchorPublisherJobIntegrationTest extends AbstractPostgresAndMongoIntegrationTest {

    private static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                    .withServices(LocalStackContainer.Service.S3);

    private static final String BUCKET = "anchor-publisher-job-integration-test";

    static {
        LOCALSTACK.start();
    }

    @DynamicPropertySource
    static void anchorProperties(DynamicPropertyRegistry registry) {
        registry.add("audit.anchor.bucket", () -> BUCKET);
    }

    @Autowired
    private AuditRecorder auditRecorder;

    @Autowired
    private PartitionRegistry partitionRegistry;

    @Autowired
    private AnchorPublisherJob anchorPublisherJob;

    @Autowired
    private Clock clock;

    @Autowired
    private CapturingListener capturingListener;

    @Autowired
    private S3Client s3Client;

    @Test
    void runNowPublishesSuccessesAndRecordsFailureWithoutAbortingOtherPartitions() throws Exception {
        s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).objectLockEnabledForBucket(true).build());

        String runId = UUID.randomUUID().toString();
        String cleanA = "anchor-job-test-" + runId + "-clean-a";
        String cleanB = "anchor-job-test-" + runId + "-clean-b";
        String noChain = "anchor-job-test-" + runId + "-no-chain";

        recordEvents(cleanA, 3);
        recordEvents(cleanB, 2);
        partitionRegistry.resolve(noChain);

        capturingListener.events.clear();
        AnchorSummary summary = anchorPublisherJob.runNow();

        assertThat(summary.storageReferenceByPartition()).containsKeys(cleanA, cleanB);
        assertThat(summary.storageReferenceByPartition()).doesNotContainKey(noChain);
        assertThat(summary.failuresByPartition()).containsOnlyKeys(noChain);
        assertThat(summary.failuresByPartition().get(noChain)).isInstanceOf(IllegalStateException.class);

        assertThat(summary.startedAt()).isNotNull();
        assertThat(summary.finishedAt()).isNotNull();
        assertThat(summary.finishedAt()).isAfterOrEqualTo(summary.startedAt());

        List<AnchorPublishFailedEvent> capturedForThisRun = capturingListener.events.stream()
                .filter(event -> event.partitionKey().equals(noChain))
                .toList();
        assertThat(capturedForThisRun).hasSize(1);

        boolean anyEventForCleanPartitions = capturingListener.events.stream()
                .anyMatch(event -> event.partitionKey().equals(cleanA) || event.partitionKey().equals(cleanB));
        assertThat(anyEventForCleanPartitions).isFalse();

        assertThat(summary.storageReferenceByPartition().get(cleanA)).contains("etag=").contains("versionId=");
        assertThat(summary.storageReferenceByPartition().get(cleanB)).contains("etag=").contains("versionId=");
        assertThat(objectCountUnderPrefix("anchors/" + cleanA + "/")).isEqualTo(1);
        assertThat(objectCountUnderPrefix("anchors/" + cleanB + "/")).isEqualTo(1);
        assertThat(objectCountUnderPrefix("anchors/" + noChain + "/")).isZero();
    }

    private int objectCountUnderPrefix(String prefix) {
        return s3Client
                .listObjectsV2(ListObjectsV2Request.builder().bucket(BUCKET).prefix(prefix).build())
                .keyCount();
    }

    private List<com.company.audit.core.api.ChainedRecord> recordEvents(String partitionKey, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> auditRecorder.record(buildEvent(partitionKey, i)))
                .toList();
    }

    private AuditEvent buildEvent(String partitionKey, int index) {
        return AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .partitionKey(partitionKey)
                .eventType("ANCHOR_JOB_TEST_EVENT")
                .actorType(ActorType.SERVICE)
                .actorId("actor-1")
                .category(AuditCategory.DATA_MUTATION)
                .occurredAt(clock.instant())
                .payload(Map.of("index", index))
                .build();
    }

    @TestConfiguration
    static class CapturingListenerConfig {

        @Bean
        CapturingListener capturingListener() {
            return new CapturingListener();
        }

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

    static class CapturingListener {

        private final List<AnchorPublishFailedEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        void onAnchorPublishFailed(AnchorPublishFailedEvent event) {
            events.add(event);
        }
    }
}
