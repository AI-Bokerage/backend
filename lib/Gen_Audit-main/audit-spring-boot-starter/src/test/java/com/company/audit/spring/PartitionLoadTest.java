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
import com.company.audit.core.port.ChainRepository;
import com.company.audit.spring.partition.PartitionRegistry;
import com.company.audit.spring.support.AbstractPostgresIntegrationTest;
import com.company.audit.spring.support.TestApplication;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Deterministic, comparable-across-runs load test: 100 concurrent appenders, across exactly 2
 * simulated independent instances (50 each, same independent-{@code ApplicationContext} pattern
 * as {@link CrossInstanceOrderingTest}), each appending 10 events sequentially — 1000 total
 * events — against one partition.
 *
 * <p><b>This phase proves distributed correctness only; it does not establish production
 * throughput.</b> Accordingly, this test's only hard pass/fail assertions are correctness ones:
 * whatever did successfully append forms a gapless, non-duplicated sequence, and the resulting
 * chain verifies {@code OK}. The retry-exhaustion rate and p50/p99 append latency are logged as
 * informational measurements — deliberately not asserted against any hardcoded threshold, since
 * real throughput characteristics for this mechanism aren't known yet; inventing a pass/fail
 * number now would repeat Phase 7's arbitrary-version-bump mistake in a different guise. See the
 * phase summary in {@code CHANGELOG.md} for the actual measured numbers this run produced and
 * whether they justified {@code audit.jpa.append-max-attempts}' chosen default.
 */
class PartitionLoadTest extends AbstractPostgresIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PartitionLoadTest.class);

    private static final int INSTANCES = 2;
    private static final int THREADS_PER_INSTANCE = 50;
    private static final int EVENTS_PER_THREAD = 10;
    private static final int TOTAL_EVENTS_REQUESTED = INSTANCES * THREADS_PER_INSTANCE * EVENTS_PER_THREAD;

    @Test
    void hundredConcurrentAppendersAcrossTwoInstancesProduceAGaplessVerifiableChain() throws Exception {
        ConfigurableApplicationContext instanceA = buildInstance();
        ConfigurableApplicationContext instanceB = buildInstance();
        try {
            AuditAppender appenderA = instanceA.getBean(AuditAppender.class);
            AuditAppender appenderB = instanceB.getBean(AuditAppender.class);
            PartitionRegistry partitionRegistry = instanceA.getBean(PartitionRegistry.class);
            Clock clock = instanceA.getBean(Clock.class);

            String partitionKey = "partition-load-test-" + UUID.randomUUID();
            PartitionContext partitionContext = partitionRegistry.resolve(partitionKey);

            int totalThreads = INSTANCES * THREADS_PER_INSTANCE;
            ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger succeeded = new AtomicInteger();
            AtomicInteger failed = new AtomicInteger();
            List<Long> latenciesMillis = new CopyOnWriteArrayList<>();

            try {
                for (int t = 0; t < totalThreads; t++) {
                    int threadIndex = t;
                    AuditAppender appender = (threadIndex % 2 == 0) ? appenderA : appenderB;
                    executor.submit(() -> {
                        try {
                            startLatch.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        for (int e = 0; e < EVENTS_PER_THREAD; e++) {
                            AuditEvent event = AuditEvent.builder()
                                    .id(UUID.randomUUID().toString())
                                    .partitionKey(partitionKey)
                                    .eventType("LOAD_TEST_EVENT")
                                    .actorType(ActorType.SERVICE)
                                    .actorId("actor-" + threadIndex)
                                    .category(AuditCategory.DATA_MUTATION)
                                    .occurredAt(clock.instant())
                                    .payload(Map.of("thread", threadIndex, "event", e))
                                    .build();
                            Instant startedAt = Instant.now();
                            try {
                                appender.append(event, partitionContext);
                                succeeded.incrementAndGet();
                            } catch (RuntimeException ex) {
                                failed.incrementAndGet();
                            } finally {
                                latenciesMillis.add(Duration.between(startedAt, Instant.now()).toMillis());
                            }
                        }
                    });
                }
                startLatch.countDown();
                executor.shutdown();
                assertThat(executor.awaitTermination(180, TimeUnit.SECONDS)).isTrue();
            } finally {
                executor.shutdownNow();
            }

            reportResults(succeeded.get(), failed.get(), latenciesMillis);

            ChainRepository chainRepository = instanceA.getBean(ChainRepository.class);
            List<ChainedRecord> stored = chainRepository.findAllOrderedBySeq(partitionKey);
            assertThat(stored).hasSize(succeeded.get());
            for (int i = 0; i < stored.size(); i++) {
                assertThat(stored.get(i).seq()).isEqualTo(i + 1L);
            }

            AuditVerifier verifier = instanceA.getBean(AuditVerifier.class);
            VerificationResult result = verifier.verify(partitionKey, partitionContext);
            assertThat(result.status()).isEqualTo(AuditChainStatus.OK);
        } finally {
            instanceA.close();
            instanceB.close();
        }
    }

    private void reportResults(int succeeded, int failed, List<Long> latenciesMillis) {
        List<Long> sorted = latenciesMillis.stream().sorted().toList();
        long p50 = sorted.isEmpty() ? 0 : sorted.get((int) (sorted.size() * 0.50));
        long p99 = sorted.isEmpty() ? 0 : sorted.get((int) Math.min(sorted.size() * 0.99, sorted.size() - 1));
        double exhaustionRate = 100.0 * failed / TOTAL_EVENTS_REQUESTED;
        log.info(
                "PartitionLoadTest results (informational, not a pass/fail gate): requested={} succeeded={} "
                        + "failed={} retryExhaustionRate={}% appendLatencyP50Ms={} appendLatencyP99Ms={}",
                TOTAL_EVENTS_REQUESTED,
                succeeded,
                failed,
                String.format("%.2f", exhaustionRate),
                p50,
                p99);
    }

    private ConfigurableApplicationContext buildInstance() {
        return new SpringApplicationBuilder(TestApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword())
                .run();
    }
}
