package com.company.audit.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.audit.core.api.AuditAppender;
import com.company.audit.core.api.AuditEvent;
import com.company.audit.core.api.AuditVerifier;
import com.company.audit.core.api.ChainedRecord;
import com.company.audit.core.api.PartitionContext;
import com.company.audit.core.api.VerificationResult;
import com.company.audit.core.api.enums.ActorType;
import com.company.audit.core.api.enums.AuditCategory;
import com.company.audit.core.api.enums.AuditChainStatus;
import com.company.audit.spring.partition.PartitionRegistry;
import com.company.audit.spring.support.AbstractPostgresIntegrationTest;
import com.company.audit.spring.support.TestApplication;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves that {@code DefaultAuditAppender}'s bounded-retry mechanism actually holds under real
 * database contention: N concurrent threads in a single JVM appending to the same partition
 * through the same {@code AuditAppender} instance.
 *
 * <p>This is deliberately not a test of distributed, multi-JVM ordering — see
 * {@code CrossInstanceOrderingTest} for that (Phase 8). This test's job is narrower: confirm
 * single-JVM contention still resolves correctly now that {@code JpaChainRepository} coordinates
 * via a Postgres advisory lock instead of the in-JVM {@code ReentrantLock} this mechanism
 * replaced — re-run with an unchanged retry budget, this test is exactly what first proved that
 * budget needed raising (see {@code AuditProperties.Jpa#getAppendMaxAttempts}'s Javadoc and
 * {@code docs/adr/0001-distributed-ordering-advisory-locks.md}).
 */
@SpringBootTest(classes = TestApplication.class)
class AuditAppenderConcurrencyTest extends AbstractPostgresIntegrationTest {

    private static final int THREAD_COUNT = 20;

    @Autowired
    private AuditAppender auditAppender;

    @Autowired
    private AuditVerifier auditVerifier;

    @Autowired
    private PartitionRegistry partitionRegistry;

    @Autowired
    private Clock clock;

    @Test
    void concurrentAppendsToSamePartitionProduceGaplessSequenceAndVerifyOk() throws Exception {
        String partitionKey = "concurrency-test-" + UUID.randomUUID();
        PartitionContext partitionContext = partitionRegistry.resolve(partitionKey);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<ChainedRecord> results = new CopyOnWriteArrayList<>();
        AtomicInteger failures = new AtomicInteger();

        try {
            for (int i = 0; i < THREAD_COUNT; i++) {
                int index = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        AuditEvent event = AuditEvent.builder()
                                .id(UUID.randomUUID().toString())
                                .partitionKey(partitionKey)
                                .eventType("CONCURRENT_EVENT")
                                .actorType(ActorType.SERVICE)
                                .actorId("actor-" + index)
                                .category(AuditCategory.DATA_MUTATION)
                                .occurredAt(clock.instant())
                                .payload(Map.of("index", index))
                                .build();
                        results.add(auditAppender.append(event, partitionContext));
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    }
                });
            }
            startLatch.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(failures.get()).isZero();
        assertThat(results).hasSize(THREAD_COUNT);

        List<Long> seqs = results.stream().map(ChainedRecord::seq).sorted().toList();
        List<Long> expected = LongStream.rangeClosed(1, THREAD_COUNT).boxed().toList();
        assertThat(seqs).isEqualTo(expected);

        VerificationResult result = auditVerifier.verify(partitionKey, partitionContext);
        assertThat(result.status()).isEqualTo(AuditChainStatus.OK);
    }
}
