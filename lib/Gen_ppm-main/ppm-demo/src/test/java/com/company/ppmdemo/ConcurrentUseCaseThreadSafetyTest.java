package com.company.ppmdemo;

import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.plan.usecase.PlanApplicationService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Empirically validates the thread-safety claim in
 * {@code ppm-core/docs/PHASE_5_READINESS_REPORT.md} §7 — every use-case
 * bean is a stateless singleton (verified there by inspecting for mutable
 * fields) — by actually hammering one concurrently, rather than trusting
 * static inspection alone. Static analysis proves there's no obvious
 * mutable field; concurrent execution proves the *behavior* holds under
 * real contention, including in the adapter beneath it.
 *
 * <p>Uses the real {@code InMemoryPlanRepositoryAdapter} (a
 * {@code ConcurrentHashMap} + {@code AtomicLong} sequence), not a mock —
 * this is as much a test of that adapter's own concurrency claims as of
 * {@code PlanApplicationServiceImpl} itself.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ConcurrentUseCaseThreadSafetyTest {

    private static final int CONCURRENT_CALLS = 200;

    @Autowired PlanApplicationService planService;

    @Test
    void concurrentPlanCreation_producesDistinctPlansWithNoDuplicateSlugsOrCorruption() throws Exception {
        UUID actorId = UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(32);
        try {
            List<Callable<Plan>> tasks = IntStream.range(0, CONCURRENT_CALLS)
                .mapToObj(i -> (Callable<Plan>) () -> planService.createPlan(
                    actorId, "concurrent-plan-" + i, "Concurrent Plan " + i,
                    null, null, PlanVisibility.PUBLIC, 0, true, null))
                .collect(Collectors.toList());

            List<Future<Plan>> futures = pool.invokeAll(tasks, 30, TimeUnit.SECONDS);

            List<Plan> results = futures.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).toList();

            assertThat(results).hasSize(CONCURRENT_CALLS);

            // No two concurrent calls raced onto the same id or the same slug —
            // proves the underlying AtomicLong sequence and ConcurrentHashMap store
            // held up under real contention, not just in single-threaded tests.
            Set<UUID> ids = results.stream().map(Plan::getId).collect(Collectors.toSet());
            Set<String> slugs = results.stream().map(Plan::getSlug).collect(Collectors.toSet());
            Set<String> codes = results.stream().map(Plan::getCode).collect(Collectors.toSet());

            assertThat(ids).hasSize(CONCURRENT_CALLS);
            assertThat(slugs).hasSize(CONCURRENT_CALLS);
            assertThat(codes).hasSize(CONCURRENT_CALLS);

            // Superset check, not an exact-size check: Spring caches the ApplicationContext
            // across test classes by default, so the shared in-memory store may already hold
            // plans created by other test classes in the same run. What matters here is that
            // every one of THIS test's concurrently-created plans is actually retrievable
            // afterward — not that it's the only thing in the store.
            Set<UUID> allPlanIds = planService.listPlans(null, null).stream()
                .map(Plan::getId).collect(Collectors.toSet());
            assertThat(allPlanIds).containsAll(ids);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentReadsOfSamePlan_returnConsistentResults() throws Exception {
        UUID actorId = UUID.randomUUID();
        Plan created = planService.createPlan(actorId, "read-heavy-plan", "Read Heavy",
            null, null, PlanVisibility.PUBLIC, 0, true, null);

        ExecutorService pool = Executors.newFixedThreadPool(16);
        AtomicInteger mismatches = new AtomicInteger(0);
        try {
            List<Callable<Void>> reads = IntStream.range(0, CONCURRENT_CALLS)
                .<Callable<Void>>mapToObj(i -> () -> {
                    Plan read = planService.getPlan(created.getId());
                    if (!read.getCode().equals("read-heavy-plan") || !read.getSlug().equals(created.getSlug())) {
                        mismatches.incrementAndGet();
                    }
                    return null;
                })
                .collect(Collectors.toList());

            pool.invokeAll(reads, 30, TimeUnit.SECONDS);
            assertThat(mismatches.get()).isZero();
        } finally {
            pool.shutdownNow();
        }
    }
}
