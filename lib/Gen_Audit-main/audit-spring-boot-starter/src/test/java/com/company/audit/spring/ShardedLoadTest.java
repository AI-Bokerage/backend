package com.company.audit.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.audit.core.api.AuditAppender;
import com.company.audit.core.api.AuditEvent;
import com.company.audit.core.api.AuditVerifier;
import com.company.audit.core.api.PartitionContext;
import com.company.audit.core.api.VerificationResult;
import com.company.audit.core.api.enums.ActorType;
import com.company.audit.core.api.enums.AuditCategory;
import com.company.audit.core.api.enums.AuditChainStatus;
import com.company.audit.core.port.ChainRepository;
import com.company.audit.spring.partition.PartitionRegistry;
import com.company.audit.spring.sharding.ConsistentHashPartitionShardResolver;
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
 * Measures, side by side, whether Phase 9A's sharding mechanism actually helps: the same
 * Phase-8-style load-test pattern (4 simulated independent instances, comparable total event
 * volume to Phase 8's 1000-event test), but spread across 20 distinct partitions instead of one,
 * run twice — once with no ownership routing at all (today's Phase 8 behavior: any instance may
 * append to any partition), once with {@link ConsistentHashPartitionShardResolver}-based routing
 * active (each instance only ever appends to partitions it owns).
 *
 * <p>This is the evidence the batching decision (recorded in {@code CHANGELOG.md} and this
 * phase's summary, never as code) is actually based on — reported plainly, not gated on any
 * invented pass/fail threshold, same discipline as Phase 8's own load test and jitter
 * before/after comparison.
 */
class ShardedLoadTest extends AbstractPostgresIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ShardedLoadTest.class);

    private static final int INSTANCES = 4;
    private static final int PARTITIONS = 20;
    private static final int THREADS = 100;
    private static final int EVENTS_PER_THREAD = 10;
    private static final int TOTAL_EVENTS = THREADS * EVENTS_PER_THREAD;

    @Test
    void shardedRoutingComparedAgainstUnshardedBaseline() throws Exception {
        List<ConfigurableApplicationContext> instances = buildInstances();
        try {
            List<AuditAppender> appenders = instances.stream().map(i -> i.getBean(AuditAppender.class)).toList();
            PartitionRegistry partitionRegistry = instances.get(0).getBean(PartitionRegistry.class);
            AuditVerifier verifier = instances.get(0).getBean(AuditVerifier.class);
            ChainRepository chainRepository = instances.get(0).getBean(ChainRepository.class);
            Clock clock = instances.get(0).getBean(Clock.class);
            ConsistentHashPartitionShardResolver resolver = new ConsistentHashPartitionShardResolver(0, INSTANCES);

            ScenarioResult unsharded = runScenario(
                    "unsharded-" + UUID.randomUUID(),
                    false,
                    appenders,
                    resolver,
                    partitionRegistry,
                    verifier,
                    chainRepository,
                    clock);
            ScenarioResult sharded = runScenario(
                    "sharded-" + UUID.randomUUID(),
                    true,
                    appenders,
                    resolver,
                    partitionRegistry,
                    verifier,
                    chainRepository,
                    clock);

            log.info(
                    "ShardedLoadTest comparison (informational, not a pass/fail gate): "
                            + "unsharded[succeeded={} failed={} exhaustionRate={}% p50Ms={} p99Ms={}] "
                            + "sharded[succeeded={} failed={} exhaustionRate={}% p50Ms={} p99Ms={}]",
                    unsharded.succeeded,
                    unsharded.failed,
                    unsharded.exhaustionRatePercent(),
                    unsharded.p50Millis(),
                    unsharded.p99Millis(),
                    sharded.succeeded,
                    sharded.failed,
                    sharded.exhaustionRatePercent(),
                    sharded.p50Millis(),
                    sharded.p99Millis());

            // Correctness must hold under both configurations, regardless of which one performs
            // better — every partition's chain, in both scenarios, must be internally consistent
            // for whatever succeeded.
            assertThat(unsharded.allPartitionChainsAreConsistent(chainRepository, verifier, partitionRegistry))
                    .as("unsharded scenario must still produce a consistent chain for every partition")
                    .isTrue();
            assertThat(sharded.allPartitionChainsAreConsistent(chainRepository, verifier, partitionRegistry))
                    .as("sharded scenario must still produce a consistent chain for every partition")
                    .isTrue();
        } finally {
            instances.forEach(ConfigurableApplicationContext::close);
        }
    }

    private ScenarioResult runScenario(
            String keyPrefix,
            boolean sharded,
            List<AuditAppender> appenders,
            ConsistentHashPartitionShardResolver resolver,
            PartitionRegistry partitionRegistry,
            AuditVerifier verifier,
            ChainRepository chainRepository,
            Clock clock)
            throws InterruptedException {
        List<String> partitionKeys = java.util.stream.IntStream.range(0, PARTITIONS)
                .mapToObj(i -> keyPrefix + "-partition-" + i)
                .toList();
        // Resolved once, up front — not inside the timed append loop below, so the per-append
        // latency measured there reflects only the append itself, not partition-context lookup.
        Map<String, com.company.audit.core.api.PartitionContext> partitionContexts = new java.util.HashMap<>();
        for (String partitionKey : partitionKeys) {
            partitionContexts.put(partitionKey, partitionRegistry.resolve(partitionKey));
        }

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<Long> latenciesMillis = new CopyOnWriteArrayList<>();

        try {
            for (int t = 0; t < THREADS; t++) {
                int threadIndex = t;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    for (int e = 0; e < EVENTS_PER_THREAD; e++) {
                        String partitionKey = partitionKeys.get((threadIndex * EVENTS_PER_THREAD + e) % PARTITIONS);
                        AuditAppender appender = sharded
                                ? appenders.get(resolver.ownerIndex(partitionKey))
                                : appenders.get(threadIndex % appenders.size());
                        AuditEvent event = AuditEvent.builder()
                                .id(UUID.randomUUID().toString())
                                .partitionKey(partitionKey)
                                .eventType("SHARDED_LOAD_TEST_EVENT")
                                .actorType(ActorType.SERVICE)
                                .actorId("actor-" + threadIndex)
                                .category(AuditCategory.DATA_MUTATION)
                                .occurredAt(clock.instant())
                                .payload(Map.of("thread", threadIndex, "event", e))
                                .build();
                        Instant startedAt = Instant.now();
                        try {
                            appender.append(event, partitionContexts.get(partitionKey));
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

        return new ScenarioResult(succeeded.get(), failed.get(), latenciesMillis, partitionKeys);
    }

    private List<ConfigurableApplicationContext> buildInstances() {
        return java.util.stream.IntStream.range(0, INSTANCES)
                .mapToObj(i -> new SpringApplicationBuilder(TestApplication.class)
                        .web(WebApplicationType.NONE)
                        .properties(
                                "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                                "spring.datasource.username=" + POSTGRES.getUsername(),
                                "spring.datasource.password=" + POSTGRES.getPassword())
                        .run())
                .toList();
    }

    private record ScenarioResult(int succeeded, int failed, List<Long> latenciesMillis, List<String> partitionKeys) {

        double exhaustionRatePercent() {
            return 100.0 * failed / TOTAL_EVENTS;
        }

        long p50Millis() {
            return percentile(0.50);
        }

        long p99Millis() {
            return percentile(0.99);
        }

        private long percentile(double fraction) {
            List<Long> sorted = latenciesMillis.stream().sorted().toList();
            if (sorted.isEmpty()) {
                return 0;
            }
            int index = (int) Math.min(sorted.size() * fraction, sorted.size() - 1);
            return sorted.get(index);
        }

        boolean allPartitionChainsAreConsistent(
                ChainRepository chainRepository, AuditVerifier verifier, PartitionRegistry partitionRegistry) {
            for (String partitionKey : partitionKeys) {
                var stored = chainRepository.findAllOrderedBySeq(partitionKey);
                for (int i = 0; i < stored.size(); i++) {
                    if (stored.get(i).seq() != i + 1L) {
                        return false;
                    }
                }
                PartitionContext context = partitionRegistry.resolve(partitionKey);
                VerificationResult result = verifier.verify(partitionKey, context);
                if (result.status() != AuditChainStatus.OK) {
                    return false;
                }
            }
            return true;
        }
    }
}
