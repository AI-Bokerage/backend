package com.company.audit.spring.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.company.audit.core.api.AuditVerifier;
import com.company.audit.core.api.PartitionContext;
import com.company.audit.core.api.VerificationResult;
import com.company.audit.core.api.enums.AuditChainStatus;
import com.company.audit.spring.metrics.NoOpAuditMetricsRecorder;
import com.company.audit.spring.partition.PartitionCatalog;
import com.company.audit.spring.scheduling.AnchorPublisherJob;
import com.company.audit.spring.verification.ChainVerifierJob;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Status;

/**
 * Proves both the reported health status and — the point of this test class — that
 * {@link AuditChainHealthIndicator#health()} never itself triggers verification: the fake
 * {@link PartitionCatalog} backing {@link #chainVerifierJob} throws if {@code listAll()} is
 * called more times than this test itself explicitly calls {@code runNow()}, so any hidden call
 * to {@code runNow()} from inside {@code health()} would fail the test immediately, not merely
 * go unnoticed.
 */
class AuditChainHealthIndicatorTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2024-06-01T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private final AtomicInteger allowedListAllCalls = new AtomicInteger(0);
    private final PartitionCatalog partitionCatalog = () -> {
        if (allowedListAllCalls.getAndDecrement() <= 0) {
            throw new AssertionError("AuditChainHealthIndicator must never trigger a verification run itself");
        }
        return List.of(new PartitionContext("health-test-partition", FIXED_INSTANT));
    };

    private AuditChainStatus nextStatus = AuditChainStatus.OK;

    private final AuditVerifier auditVerifier = (partitionKey, partitionContext) -> nextStatus == AuditChainStatus.OK
            ? VerificationResult.ok(partitionKey)
            : VerificationResult.brokenAt(partitionKey, nextStatus, 1L, "tampered");

    private final ChainVerifierJob chainVerifierJob = new ChainVerifierJob(
            partitionCatalog, auditVerifier, event -> { }, new NoOpAuditMetricsRecorder(), FIXED_CLOCK);

    private final ObjectProvider<AnchorPublisherJob> noAnchorJobProvider = new ObjectProvider<>() {
        @Override
        public AnchorPublisherJob getObject() {
            throw new AssertionError("not exercised by this test");
        }

        @Override
        public AnchorPublisherJob getIfAvailable() {
            return null;
        }
    };

    private final AuditChainHealthIndicator indicator =
            new AuditChainHealthIndicator(chainVerifierJob, noAnchorJobProvider);

    @Test
    void withNoRunCompletedYetReportsUnknownNotUp() {
        assertThatCode(indicator::health).doesNotThrowAnyException();

        var health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails()).containsKey("reason");
    }

    @Test
    void afterCleanRunReportsUp() {
        allowedListAllCalls.set(1);
        nextStatus = AuditChainStatus.OK;
        chainVerifierJob.runNow();

        var health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("cleanPartitions", 1L);
        assertThat(health.getDetails()).containsEntry("brokenPartitions", 0L);
    }

    @Test
    void afterBrokenRunReportsDown() {
        allowedListAllCalls.set(1);
        nextStatus = AuditChainStatus.HASH_MISMATCH;
        chainVerifierJob.runNow();

        var health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("cleanPartitions", 0L);
        assertThat(health.getDetails()).containsEntry("brokenPartitions", 1L);
    }

    @Test
    void healthNeverCallsRunNowItself() {
        allowedListAllCalls.set(1);
        chainVerifierJob.runNow();

        // allowedListAllCalls is now exhausted (0) — if health() called runNow() again, the fake
        // PartitionCatalog above would throw, failing this test.
        assertThatCode(indicator::health).doesNotThrowAnyException();
        assertThatCode(indicator::health).doesNotThrowAnyException();
    }
}
