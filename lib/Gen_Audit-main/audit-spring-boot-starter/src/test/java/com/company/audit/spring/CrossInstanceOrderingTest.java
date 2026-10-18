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
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The core proof for Phase 8: constructs <b>two fully independent</b> Spring
 * {@link ConfigurableApplicationContext}s — each with its own {@code DataSource},
 * {@code EntityManagerFactory}, and {@code JpaChainRepository} bean, sharing no Java object with
 * the other beyond this test method itself — both pointed at the same Testcontainers Postgres
 * instance, and fires concurrent, high-contention {@code append()} calls against the same
 * partition from both.
 *
 * <p>This is deliberately not just "many threads in one JVM again": {@link #buildInstance()}
 * boots a genuinely separate {@code SpringApplicationBuilder} run each time, so the two
 * repositories below could not possibly coordinate via any in-JVM mechanism (no shared lock, no
 * shared map) — only the Postgres-level advisory lock this phase introduces can be responsible if
 * this test passes.
 */
class CrossInstanceOrderingTest extends AbstractPostgresIntegrationTest {

    private static final int THREADS_PER_INSTANCE = 10;

    @Test
    void concurrentAppendsFromTwoIndependentInstancesProduceGaplessSequenceAndVerifyOk() throws Exception {
        ConfigurableApplicationContext instanceA = buildInstance();
        ConfigurableApplicationContext instanceB = buildInstance();
        try {
            AuditAppender appenderA = instanceA.getBean(AuditAppender.class);
            AuditAppender appenderB = instanceB.getBean(AuditAppender.class);
            PartitionRegistry partitionRegistry = instanceA.getBean(PartitionRegistry.class);
            Clock clockA = instanceA.getBean(Clock.class);

            String partitionKey = "cross-instance-test-" + UUID.randomUUID();
            PartitionContext partitionContext = partitionRegistry.resolve(partitionKey);

            int totalThreads = THREADS_PER_INSTANCE * 2;
            ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
            CountDownLatch startLatch = new CountDownLatch(1);
            List<ChainedRecord> results = new CopyOnWriteArrayList<>();
            AtomicInteger failures = new AtomicInteger();

            try {
                for (int i = 0; i < totalThreads; i++) {
                    int index = i;
                    AuditAppender appender = (index % 2 == 0) ? appenderA : appenderB;
                    executor.submit(() -> {
                        try {
                            startLatch.await();
                            AuditEvent event = AuditEvent.builder()
                                    .id(UUID.randomUUID().toString())
                                    .partitionKey(partitionKey)
                                    .eventType("CROSS_INSTANCE_EVENT")
                                    .actorType(ActorType.SERVICE)
                                    .actorId("actor-" + index)
                                    .category(AuditCategory.DATA_MUTATION)
                                    .occurredAt(clockA.instant())
                                    .payload(Map.of("index", index))
                                    .build();
                            results.add(appender.append(event, partitionContext));
                        } catch (Exception e) {
                            failures.incrementAndGet();
                        }
                    });
                }
                startLatch.countDown();
                executor.shutdown();
                assertThat(executor.awaitTermination(120, TimeUnit.SECONDS)).isTrue();
            } finally {
                executor.shutdownNow();
            }

            assertThat(failures.get()).isZero();
            assertThat(results).hasSize(totalThreads);

            List<Long> seqs = results.stream().map(ChainedRecord::seq).sorted().toList();
            List<Long> expected = LongStream.rangeClosed(1, totalThreads).boxed().toList();
            assertThat(seqs).isEqualTo(expected);

            // Confirm no seq is duplicated in storage itself, not just in the in-memory results
            // list — a genuine cross-instance ordering bug could in principle let two writers
            // both believe they succeeded with the same seq if the lock weren't real.
            ChainRepository chainRepository = instanceA.getBean(ChainRepository.class);
            List<ChainedRecord> stored = chainRepository.findAllOrderedBySeq(partitionKey);
            assertThat(stored).hasSize(totalThreads);
            assertThat(stored.stream().map(ChainedRecord::seq).distinct().count()).isEqualTo(totalThreads);

            AuditVerifier verifier = instanceA.getBean(AuditVerifier.class);
            VerificationResult result = verifier.verify(partitionKey, partitionContext);
            assertThat(result.status()).isEqualTo(AuditChainStatus.OK);
        } finally {
            instanceA.close();
            instanceB.close();
        }
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
